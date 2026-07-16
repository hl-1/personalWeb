package com.studystack.identity.application;

import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.ExternalIdentity;
import com.studystack.identity.domain.ExternalIdentityRepository;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Lazy
public final class IdentityBindingService {

    private static final String GITHUB = "github";
    private static final String PROVIDER_SUBJECT_CONSTRAINT =
            "uk_identity_external_provider_subject";

    private final UserAccountRepository userAccounts;
    private final ExternalIdentityRepository externalIdentities;
    private final TransactionTemplate transactions;

    public IdentityBindingService(
            UserAccountRepository userAccounts,
            ExternalIdentityRepository externalIdentities,
            PlatformTransactionManager transactionManager) {
        this.userAccounts = Objects.requireNonNull(userAccounts, "userAccounts is required");
        this.externalIdentities = Objects.requireNonNull(
                externalIdentities, "externalIdentities is required");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager is required"));
    }

    public AuthenticatedIdentity bind(GitHubIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims is required");
        try {
            return transactions.execute(status -> bindInTransaction(claims));
        } catch (RuntimeException exception) {
            if (!isProviderSubjectConflict(exception)) {
                throw exception;
            }
            return transactions.execute(status -> updateExistingIdentity(claims));
        }
    }

    private AuthenticatedIdentity bindInTransaction(GitHubIdentityClaims claims) {
        return externalIdentities.findByProviderAndProviderSubject(
                        GITHUB, claims.providerSubject())
                .map(identity -> updateExistingIdentity(identity, claims))
                .orElseGet(() -> createIdentity(claims));
    }

    private AuthenticatedIdentity createIdentity(GitHubIdentityClaims claims) {
        OffsetDateTime timestamp = now();
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount(
                userId,
                claims.login(),
                claims.displayName(),
                claims.avatarUrl(),
                AccountStatus.ACTIVE,
                timestamp,
                timestamp,
                timestamp);
        userAccounts.save(account);
        externalIdentities.save(ExternalIdentity.github(
                UUID.randomUUID(), userId, claims.providerSubject(), timestamp));
        return authenticated(account, claims.providerSubject());
    }

    private AuthenticatedIdentity updateExistingIdentity(GitHubIdentityClaims claims) {
        ExternalIdentity identity = externalIdentities.findByProviderAndProviderSubject(
                        GITHUB, claims.providerSubject())
                .orElseThrow(() -> new IllegalStateException("Concurrent identity binding is missing"));
        return updateExistingIdentity(identity, claims);
    }

    private AuthenticatedIdentity updateExistingIdentity(
            ExternalIdentity identity,
            GitHubIdentityClaims claims) {
        UserAccount account = userAccounts.findById(identity.userId())
                .orElseThrow(() -> new IllegalStateException("Bound user account is missing"));
        if (account.status() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }

        account.updateProfile(
                claims.login(), claims.displayName(), claims.avatarUrl(), now());
        userAccounts.save(account);
        return authenticated(account, claims.providerSubject());
    }

    private AuthenticatedIdentity authenticated(UserAccount account, String providerSubject) {
        return new AuthenticatedIdentity(
                account.id(),
                providerSubject,
                account.login(),
                account.displayName(),
                account.avatarUrl());
    }

    private boolean isProviderSubjectConflict(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && PROVIDER_SUBJECT_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            if (cause == cause.getCause()) {
                return false;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static final class AccountDisabledException extends RuntimeException {

        private AccountDisabledException() {
            super("Account is disabled");
        }
    }
}
