package com.studystack.admin.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
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
        "springdoc.swagger-ui.enabled=false"
})
class AdminAuditSchemaIntegrationTest {

    private static final Set<String> ACTIONS = Set.of("CREATE", "UPDATE", "DELETE", "PUBLISH", "ARCHIVE");
    private static final Set<String> RESOURCE_TYPES = Set.of(
            "ARTICLE", "CATEGORY", "TAG", "PROJECT", "PROFILE", "SKILL", "EXPERIENCE");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanDatabase() {
        Boolean auditTableExists = jdbcTemplate.queryForObject(
                "select to_regclass('admin_audit_log') is not null",
                Boolean.class);
        if (Boolean.TRUE.equals(auditTableExists)) {
            jdbcTemplate.execute(
                    "truncate table admin_audit_log, identity_external_identity, identity_user_account cascade");
        }
    }

    @Test
    void appliesAdminAuditMigrationAfterPortfolio() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertTrue(appliedVersions.size() >= 5, "V5 admin audit migration must be applied");
        assertEquals(List.of("1", "2", "3", "4", "5"), appliedVersions.subList(0, 5));
    }

    @Test
    void createsTheExactAuditColumnContract() {
        Map<String, ColumnDefinition> columns = jdbcTemplate.query(
                        """
                        select column_name, data_type, is_nullable,
                               character_maximum_length, column_default
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name = 'admin_audit_log'
                        """,
                        (resultSet, rowNumber) -> new ColumnDefinition(
                                resultSet.getString("column_name"),
                                resultSet.getString("data_type"),
                                "YES".equals(resultSet.getString("is_nullable")),
                                (Integer) resultSet.getObject("character_maximum_length"),
                                resultSet.getString("column_default")))
                .stream()
                .collect(Collectors.toMap(ColumnDefinition::name, column -> column));

        assertEquals(7, columns.size());
        assertColumn(columns, "id", "uuid", false, null);
        assertColumn(columns, "actor_user_id", "uuid", false, null);
        assertColumn(columns, "action", "character varying", false, 16);
        assertColumn(columns, "resource_type", "character varying", false, 16);
        assertColumn(columns, "resource_id", "uuid", false, null);
        assertColumn(columns, "resource_version", "bigint", true, null);
        assertColumn(columns, "occurred_at", "timestamp with time zone", false, null);
        assertTrue(columns.values().stream().allMatch(column -> column.defaultValue() == null));
    }

    @Test
    void createsStableAuditConstraintsAndIndexes() {
        Map<String, String> constraints = auditConstraints();

        assertEquals(Set.of(
                "pk_admin_audit_log",
                "fk_admin_audit_actor_user",
                "ck_admin_audit_action",
                "ck_admin_audit_resource_type",
                "ck_admin_audit_resource_version"), constraints.keySet());
        assertContains(constraints, "fk_admin_audit_actor_user", "identity_user_account");
        assertFalse(constraints.get("fk_admin_audit_actor_user").contains("ON DELETE CASCADE"));
        ACTIONS.forEach(action -> assertContains(constraints, "ck_admin_audit_action", action));
        RESOURCE_TYPES.forEach(type -> assertContains(constraints, "ck_admin_audit_resource_type", type));
        assertContains(constraints, "ck_admin_audit_resource_version", "resource_version", ">= 0");

        Map<String, String> indexes = auditIndexes();
        assertEquals(Set.of("ix_admin_audit_occurred_at", "ix_admin_audit_resource"), indexes.keySet());
        assertContains(indexes, "ix_admin_audit_occurred_at", "occurred_at DESC", "id DESC");
        assertContains(
                indexes,
                "ix_admin_audit_resource",
                "resource_type",
                "resource_id",
                "occurred_at DESC");
    }

    @Test
    void acceptsOnlyApprovedActionsAndResourceTypes() {
        UUID actorId = insertUser();

        for (String action : ACTIONS) {
            insertAudit(actorId, action, "ARTICLE", 0L);
        }
        for (String resourceType : RESOURCE_TYPES) {
            insertAudit(actorId, "CREATE", resourceType, null);
        }

        assertEquals(ACTIONS.size() + RESOURCE_TYPES.size(), auditCount());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertAudit(actorId, "RESTORE", "ARTICLE", 0L));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertAudit(actorId, "CREATE", "COMMENT", 0L));
    }

    @Test
    void rejectsMissingOrUnknownActorAndNegativeVersion() {
        UUID actorId = insertUser();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertAudit(null, "CREATE", "ARTICLE", 0L));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertAudit(UUID.randomUUID(), "CREATE", "ARTICLE", 0L));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertAudit(actorId, "CREATE", "ARTICLE", -1L));
    }

    @Test
    void preventsAuditUpdatesAndDeletes() {
        UUID actorId = insertUser();
        UUID auditId = insertAudit(actorId, "CREATE", "ARTICLE", 0L);

        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        "update admin_audit_log set resource_version = 1 where id = ?", auditId));
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("delete from admin_audit_log where id = ?", auditId));
        assertEquals(1, auditCount());
    }

    @Test
    void rollsBackAuditRowsWithTheSurroundingTransaction() {
        UUID actorId = insertUser();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            insertAudit(actorId, "UPDATE", "PROJECT", 4L);
            throw new IllegalStateException("force rollback");
        }));

        assertEquals(0, auditCount());
    }

    @Test
    void allowsTruncateForTestCleanupWithoutWeakeningAppendOnlyProtection() {
        UUID actorId = insertUser();
        insertAudit(actorId, "DELETE", "TAG", null);

        jdbcTemplate.execute("truncate table admin_audit_log");

        assertEquals(0, auditCount());
        UUID replacementId = insertAudit(actorId, "CREATE", "TAG", 0L);
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("delete from admin_audit_log where id = ?", replacementId));
    }

    private Map<String, String> auditConstraints() {
        return jdbcTemplate.query(
                        """
                        select constraint_name, pg_get_constraintdef(pg_constraint.oid) as definition
                        from information_schema.table_constraints
                        join pg_constraint on pg_constraint.conname = constraint_name
                        join pg_class on pg_class.oid = pg_constraint.conrelid
                                     and pg_class.relname = table_name
                        where table_schema = current_schema()
                          and table_name = 'admin_audit_log'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("constraint_name"),
                                resultSet.getString("definition")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> auditIndexes() {
        return jdbcTemplate.query(
                        """
                        select indexname, indexdef
                        from pg_indexes
                        where schemaname = current_schema()
                          and tablename = 'admin_audit_log'
                          and indexname like 'ix_admin_audit_%'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("indexname"),
                                resultSet.getString("indexdef")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
                "audit-" + userId,
                "Audit Test User",
                null,
                now,
                now,
                now);
        return userId;
    }

    private UUID insertAudit(UUID actorId, String action, String resourceType, Long resourceVersion) {
        UUID auditId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into admin_audit_log (
                    id, actor_user_id, action, resource_type,
                    resource_id, resource_version, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                auditId,
                actorId,
                action,
                resourceType,
                UUID.randomUUID(),
                resourceVersion,
                Timestamp.from(Instant.now()));
        return auditId;
    }

    private int auditCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from admin_audit_log", Integer.class);
        assertNotNull(count);
        return count;
    }

    private static void assertColumn(
            Map<String, ColumnDefinition> columns,
            String name,
            String type,
            boolean nullable,
            Integer maximumLength) {
        ColumnDefinition actual = columns.get(name);
        assertNotNull(actual, name + " must exist");
        assertEquals(new ColumnDefinition(name, type, nullable, maximumLength, null), actual);
    }

    private static void assertContains(Map<String, String> definitions, String name, String... fragments) {
        String definition = definitions.get(name);
        assertNotNull(definition, name + " must exist");
        for (String fragment : fragments) {
            assertTrue(definition.contains(fragment), name + " must contain " + fragment + ": " + definition);
        }
    }

    private record ColumnDefinition(
            String name,
            String type,
            boolean nullable,
            Integer maximumLength,
            String defaultValue) {
    }
}
