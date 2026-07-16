package com.studystack.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.ExternalIdentity;
import com.studystack.identity.domain.ExternalIdentityRepository;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "springdoc.swagger-ui.enabled=false",
        "spring.session.jdbc.cleanup-cron=-"
})
class IdentityBindingServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    IdentityBindingService bindingService;

    @Autowired
    UserAccountRepository userAccounts;

    @Autowired
    ExternalIdentityRepository externalIdentities;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearIdentityData() {
        jdbcTemplate.update("delete from identity_external_identity");
        jdbcTemplate.update("delete from identity_user_account");
    }

    @Test
    void createsUserAndGithubIdentityOnFirstLogin() {
        GitHubIdentityClaims claims = claims("30001", "octocat", "The Octocat", null);

        AuthenticatedIdentity authenticated = bindingService.bind(claims);

        assertEquals(claims.providerSubject(), authenticated.providerSubject());
        assertEquals("octocat", authenticated.login());
        assertEquals("The Octocat", authenticated.displayName());
        assertEquals(1, rowCount("identity_user_account"));
        assertEquals(1, rowCount("identity_external_identity"));
        UserAccount stored = inTransaction(
                () -> userAccounts.findById(authenticated.userId()).orElseThrow());
        assertEquals(AccountStatus.ACTIVE, stored.status());
    }

    @Test
    void repeatedLoginKeepsUuidAndUpdatesMutableProfile() {
        AuthenticatedIdentity first = bindingService.bind(
                claims("30002", "old-login", "Old Name", "https://avatars.example/old.png"));
        UserAccount before = inTransaction(() -> userAccounts.findById(first.userId()).orElseThrow());

        AuthenticatedIdentity second = bindingService.bind(
                claims("30002", "new-login", "New Name", null));

        assertEquals(first.userId(), second.userId());
        assertEquals("new-login", second.login());
        assertEquals("New Name", second.displayName());
        assertEquals(1, rowCount("identity_user_account"));
        assertEquals(1, rowCount("identity_external_identity"));
        UserAccount after = inTransaction(() -> userAccounts.findById(first.userId()).orElseThrow());
        assertEquals("new-login", after.login());
        assertEquals("New Name", after.displayName());
        assertNull(after.avatarUrl());
        assertNotEquals(before.lastLoginAt(), after.lastLoginAt());
    }

    @Test
    void rejectsDisabledAccountWithoutChangingProfile() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime timestamp = now();
        inTransaction(() -> {
            userAccounts.save(new UserAccount(
                    userId,
                    "disabled-login",
                    "Disabled User",
                    null,
                    AccountStatus.DISABLED,
                    timestamp,
                    timestamp,
                    timestamp));
            externalIdentities.save(ExternalIdentity.github(
                    UUID.randomUUID(), userId, "30003", timestamp));
        });

        assertThrows(
                IdentityBindingService.AccountDisabledException.class,
                () -> bindingService.bind(claims("30003", "changed", "Changed", null)));

        UserAccount stored = inTransaction(() -> userAccounts.findById(userId).orElseThrow());
        assertEquals("disabled-login", stored.login());
        assertEquals("Disabled User", stored.displayName());
    }

    @Test
    void concurrentFirstLoginConvergesOnOneBinding()
            throws ExecutionException, InterruptedException, TimeoutException {
        CyclicBarrier emptyLookupBarrier = new CyclicBarrier(2);
        ExternalIdentityRepository coordinatedIdentities = new CoordinatedExternalIdentityRepository(
                externalIdentities, emptyLookupBarrier);
        IdentityBindingService coordinatedService = new IdentityBindingService(
                userAccounts, coordinatedIdentities, transactionManager);
        GitHubIdentityClaims claims = claims("30004", "octocat", "The Octocat", null);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AuthenticatedIdentity> first = executor.submit(() -> coordinatedService.bind(claims));
            Future<AuthenticatedIdentity> second = executor.submit(() -> coordinatedService.bind(claims));

            AuthenticatedIdentity firstResult = first.get(30, TimeUnit.SECONDS);
            AuthenticatedIdentity secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(firstResult.userId(), secondResult.userId());
        }
        assertEquals(1, rowCount("identity_user_account"));
        assertEquals(1, rowCount("identity_external_identity"));
    }

    @Test
    void failedIdentityInsertRollsBackNewUserAndPropagatesTheIntegrityError() {
        GitHubIdentityClaims invalidClaims = claims("3".repeat(65), "octocat", "The Octocat", null);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> bindingService.bind(invalidClaims));

        assertEquals(0, rowCount("identity_user_account"));
        assertEquals(0, rowCount("identity_external_identity"));
    }

    @Test
    void unchangedRepeatedLoginRemainsIdempotent() {
        GitHubIdentityClaims claims = claims("30005", "octocat", "The Octocat", null);

        AuthenticatedIdentity first = bindingService.bind(claims);
        AuthenticatedIdentity second = bindingService.bind(claims);

        assertEquals(first.userId(), second.userId());
        assertEquals(1, rowCount("identity_user_account"));
        assertEquals(1, rowCount("identity_external_identity"));
    }

    @Test
    void authenticatedIdentityHasOnlyApprovedFieldsAndRedactsSubject() {
        Set<String> components = Arrays.stream(AuthenticatedIdentity.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                UUID.randomUUID(), "30006", "octocat", "The Octocat", null);

        assertEquals(
                Set.of("userId", "providerSubject", "login", "displayName", "avatarUrl"),
                components);
        assertEquals("AuthenticatedIdentity[redacted]", identity.toString());
        assertFalse(identity.toString().contains(identity.providerSubject()));
    }

    private GitHubIdentityClaims claims(
            String subject,
            String login,
            String displayName,
            String avatarUrl) {
        return new GitHubIdentityClaims(subject, login, displayName, avatarUrl);
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static final class CoordinatedExternalIdentityRepository
            implements ExternalIdentityRepository {

        private final ExternalIdentityRepository delegate;
        private final CyclicBarrier emptyLookupBarrier;

        private CoordinatedExternalIdentityRepository(
                ExternalIdentityRepository delegate,
                CyclicBarrier emptyLookupBarrier) {
            this.delegate = delegate;
            this.emptyLookupBarrier = emptyLookupBarrier;
        }

        @Override
        public ExternalIdentity save(ExternalIdentity identity) {
            return delegate.save(identity);
        }

        @Override
        public Optional<ExternalIdentity> findByProviderAndProviderSubject(
                String provider,
                String providerSubject) {
            Optional<ExternalIdentity> identity = delegate.findByProviderAndProviderSubject(
                    provider, providerSubject);
            if (identity.isEmpty()) {
                awaitConcurrentLookup();
            }
            return identity;
        }

        private void awaitConcurrentLookup() {
            try {
                emptyLookupBarrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Concurrent lookup interrupted", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new IllegalStateException("Concurrent lookup did not converge", exception);
            }
        }
    }
}
