package com.studystack.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.admin.domain.AdminAuditAction;
import com.studystack.admin.domain.AdminAuditEntry;
import com.studystack.admin.domain.AdminAuditRepository;
import com.studystack.admin.domain.AdminResourceType;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class AdminAuditServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    AdminActorResolver actorResolver;

    @Autowired
    AdminAuditService auditService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanDatabaseAndSecurityContext() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.execute(
                """
                truncate table admin_audit_log, content_article_tag, content_article,
                    content_category, content_tag, identity_external_identity,
                    identity_user_account cascade
                """);
    }

    @Test
    void resolvesTheAuthenticatedAdminLocalUserId() {
        UUID userId = UUID.randomUUID();
        authenticate(userId.toString(), true, "ROLE_USER", "ROLE_ADMIN");

        assertEquals(new AdminActor(userId), actorResolver.resolve());
    }

    @Test
    void rejectsAnonymousUnauthenticatedInvalidAndNonAdminPrincipals() {
        assertDeniedWithoutLeakingPrincipal(null, null);

        Authentication anonymous = new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertDeniedWithoutLeakingPrincipal(anonymous, "anonymousUser");

        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(
                "unauthenticated-user", "unused");
        assertDeniedWithoutLeakingPrincipal(unauthenticated, "unauthenticated-user");

        Authentication invalidName = authentication("not-a-local-uuid", true, "ROLE_ADMIN");
        assertDeniedWithoutLeakingPrincipal(invalidName, "not-a-local-uuid");

        String nonAdminId = UUID.randomUUID().toString();
        Authentication nonAdmin = authentication(nonAdminId, true, "ROLE_USER");
        assertDeniedWithoutLeakingPrincipal(nonAdmin, nonAdminId);
    }

    @Test
    void appendsEveryAllowedActionResourceCombinationWithOnlyApprovedFields() {
        UUID actorId = insertUser();
        authenticate(actorId.toString(), true, "ROLE_USER", "ROLE_ADMIN");

        int expectedRows = 0;
        for (AdminAuditAction action : AdminAuditAction.values()) {
            for (AdminResourceType resourceType : AdminResourceType.values()) {
                if (!action.supports(resourceType)) {
                    continue;
                }
                Long resourceVersion = expectedRows % 2 == 0 ? (long) expectedRows : null;
                auditService.record(
                        action,
                        resourceType,
                        UUID.randomUUID(),
                        resourceVersion);
                expectedRows++;
            }
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from admin_audit_log order by occurred_at, id");
        assertEquals(expectedRows, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.keySet().equals(Set.of(
                "id",
                "actor_user_id",
                "action",
                "resource_type",
                "resource_id",
                "resource_version",
                "occurred_at"))));
        assertTrue(rows.stream().allMatch(row -> actorId.equals(row.get("actor_user_id"))));
        assertTrue(rows.stream().allMatch(row -> row.get("occurred_at") != null));
    }

    @Test
    void rejectsUnsupportedActionResourceCombinationsAndNegativeVersions() {
        UUID actorId = insertUser();
        authenticate(actorId.toString(), true, "ROLE_ADMIN");

        assertThrows(
                IllegalArgumentException.class,
                () -> auditService.record(
                        AdminAuditAction.PUBLISH,
                        AdminResourceType.CATEGORY,
                        UUID.randomUUID(),
                        0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> auditService.record(
                        AdminAuditAction.CREATE,
                        AdminResourceType.ARTICLE,
                        UUID.randomUUID(),
                        -1L));
        assertEquals(0, auditCount());
    }

    @Test
    void rollsBackAuditWhenTheBusinessTransactionFails() {
        UUID actorId = insertUser();
        authenticate(actorId.toString(), true, "ROLE_ADMIN");
        UUID categoryId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            insertCategory(categoryId, "business-rollback");
            auditService.record(
                    AdminAuditAction.CREATE,
                    AdminResourceType.CATEGORY,
                    categoryId,
                    0L);
            throw new IllegalStateException("simulate business failure");
        }));

        assertEquals(0, categoryCount(categoryId));
        assertEquals(0, auditCount());
    }

    @Test
    void rollsBackBusinessChangesWhenAuditPersistenceFails() {
        authenticate(UUID.randomUUID().toString(), true, "ROLE_ADMIN");
        UUID categoryId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
            insertCategory(categoryId, "audit-rollback");
            auditService.record(
                    AdminAuditAction.CREATE,
                    AdminResourceType.CATEGORY,
                    categoryId,
                    0L);
        }));

        assertEquals(0, categoryCount(categoryId));
        assertEquals(0, auditCount());
    }

    @Test
    void exposesOnlyAppendOnTheRepositoryContract() {
        Set<String> methods = Arrays.stream(AdminAuditRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("append"), methods);
    }

    @Test
    void keepsPrincipalAndSensitiveContentOutOfExceptionsLogsAndEntityText(CapturedOutput output) {
        String sensitivePrincipal = "markdown-session-oauth-provider-subject";
        authenticate(sensitivePrincipal, true, "ROLE_ADMIN");

        AccessDeniedException failure = assertThrows(
                AccessDeniedException.class,
                actorResolver::resolve);

        assertFalse(failure.getMessage().contains(sensitivePrincipal));
        assertFalse(output.getAll().contains(sensitivePrincipal));

        AdminAuditEntry entry = new AdminAuditEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AdminAuditAction.UPDATE,
                AdminResourceType.ARTICLE,
                UUID.randomUUID(),
                3L,
                Instant.parse("2026-07-20T00:00:00Z"));
        String description = entry.toString().toLowerCase();
        assertFalse(description.contains("markdown"));
        assertFalse(description.contains("session"));
        assertFalse(description.contains("oauth"));
        assertFalse(description.contains("provider"));
    }

    private void assertDeniedWithoutLeakingPrincipal(Authentication authentication, String principalName) {
        SecurityContextHolder.clearContext();
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        AccessDeniedException failure = assertThrows(AccessDeniedException.class, actorResolver::resolve);

        if (principalName != null) {
            assertFalse(failure.getMessage().contains(principalName));
        }
    }

    private void authenticate(String name, boolean authenticated, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(authentication(name, authenticated, authorities));
    }

    private static Authentication authentication(String name, boolean authenticated, String... authorities) {
        Collection<SimpleGrantedAuthority> grantedAuthorities = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return authenticated
                ? UsernamePasswordAuthenticationToken.authenticated(name, "unused", grantedAuthorities)
                : UsernamePasswordAuthenticationToken.unauthenticated(name, "unused");
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into identity_user_account (
                    id, login, display_name, avatar_url, status,
                    created_at, updated_at, last_login_at)
                values (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                userId,
                "audit-service-" + userId,
                "Audit Service User",
                null,
                now,
                now,
                now);
        return userId;
    }

    private void insertCategory(UUID categoryId, String slug) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into content_category (id, name, slug, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                categoryId,
                "Audit category",
                slug,
                now,
                now);
    }

    private int categoryCount(UUID categoryId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from content_category where id = ?",
                Integer.class,
                categoryId);
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject("select count(*) from admin_audit_log", Integer.class);
    }
}
