package com.studystack.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.ExternalIdentity;
import com.studystack.identity.domain.ExternalIdentityRepository;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class IdentityRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    UserAccountRepository userAccounts;

    @Autowired
    ExternalIdentityRepository externalIdentities;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistsAndFindsUserByPublicUuidWithNullableAvatar() {
        UserAccount account = newUser(AccountStatus.ACTIVE, null);
        inTransaction(() -> {
            userAccounts.save(account);
            entityManager.flush();
        });

        UserAccount reloaded = inTransaction(() -> userAccounts.findById(account.id()).orElseThrow());

        assertEquals(account.id(), reloaded.id());
        assertEquals(AccountStatus.ACTIVE, reloaded.status());
        assertNull(reloaded.avatarUrl());
    }

    @Test
    void preservesActiveAndDisabledAccountStates() {
        UserAccount active = newUser(AccountStatus.ACTIVE, "https://avatars.example/active.png");
        UserAccount disabled = newUser(AccountStatus.DISABLED, null);
        inTransaction(() -> {
            userAccounts.save(active);
            userAccounts.save(disabled);
            entityManager.flush();
        });

        assertEquals(AccountStatus.ACTIVE,
                inTransaction(() -> userAccounts.findById(active.id()).orElseThrow()).status());
        assertEquals(AccountStatus.DISABLED,
                inTransaction(() -> userAccounts.findById(disabled.id()).orElseThrow()).status());
    }

    @Test
    void findsGithubIdentityWithoutUsingSubjectAsPublicUserId() {
        UserAccount account = newUser(AccountStatus.ACTIVE, null);
        String providerSubject = "20001";
        ExternalIdentity identity = ExternalIdentity.github(
                UUID.randomUUID(), account.id(), providerSubject, now());
        inTransaction(() -> {
            userAccounts.save(account);
            externalIdentities.save(identity);
            entityManager.flush();
        });

        ExternalIdentity reloaded = inTransaction(() -> externalIdentities
                .findByProviderAndProviderSubject("github", providerSubject)
                .orElseThrow());

        assertEquals(account.id(), reloaded.userId());
        assertEquals("github", reloaded.provider());
        assertFalse(reloaded.toString().contains(providerSubject));
    }

    @Test
    void surfacesProviderSubjectUniquenessViolation() {
        UserAccount first = newUser(AccountStatus.ACTIVE, null);
        UserAccount second = newUser(AccountStatus.ACTIVE, null);
        inTransaction(() -> {
            userAccounts.save(first);
            userAccounts.save(second);
            externalIdentities.save(ExternalIdentity.github(
                    UUID.randomUUID(), first.id(), "20002", now()));
            entityManager.flush();
        });

        ConstraintViolationException exception = assertThrows(
                ConstraintViolationException.class,
                () -> inTransaction(() -> {
            externalIdentities.save(ExternalIdentity.github(
                    UUID.randomUUID(), second.id(), "20002", now()));
            entityManager.flush();
        }));

        assertEquals("uk_identity_external_provider_subject", exception.getConstraintName());
    }

    @Test
    void rejectsStaleUserUpdateWithOptimisticLock() {
        UserAccount account = newUser(AccountStatus.ACTIVE, null);
        inTransaction(() -> {
            userAccounts.save(account);
            entityManager.flush();
        });
        UserAccount firstCopy = inTransaction(() -> userAccounts.findById(account.id()).orElseThrow());
        UserAccount staleCopy = inTransaction(() -> userAccounts.findById(account.id()).orElseThrow());

        firstCopy.updateProfile("updated-login", "Updated User", null, now());
        inTransaction(() -> {
            userAccounts.save(firstCopy);
            entityManager.flush();
        });

        staleCopy.updateProfile("stale-login", "Stale User", null, now());
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> inTransaction(() -> {
            userAccounts.save(staleCopy);
            entityManager.flush();
        }));
    }

    private UserAccount newUser(AccountStatus status, String avatarUrl) {
        OffsetDateTime timestamp = now();
        UUID id = UUID.randomUUID();
        return new UserAccount(
                id,
                "user-" + id,
                "Test User",
                avatarUrl,
                status,
                timestamp,
                timestamp,
                timestamp);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
