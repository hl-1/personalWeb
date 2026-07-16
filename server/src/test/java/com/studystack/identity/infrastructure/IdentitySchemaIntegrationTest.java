package com.studystack.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class IdentitySchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appliesIdentityMigrationAfterFoundationBaseline() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertEquals(List.of("1", "2"), appliedVersions);
    }

    @Test
    void createsOnlyTheApprovedIdentityAndSessionTables() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = current_schema()
                order by table_name
                """,
                String.class);

        assertEquals(List.of(
                "flyway_schema_history",
                "identity_external_identity",
                "identity_user_account",
                "spring_session",
                "spring_session_attributes"), tables);
    }

    @Test
    void createsTheApprovedColumnContract() {
        Map<String, ColumnDefinition> columns = jdbcTemplate.query(
                        """
                        select table_name, column_name, data_type, is_nullable,
                               character_maximum_length, column_default
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name in (
                              'identity_user_account',
                              'identity_external_identity',
                              'spring_session',
                              'spring_session_attributes')
                        """,
                        (resultSet, rowNumber) -> new ColumnDefinition(
                                resultSet.getString("table_name"),
                                resultSet.getString("column_name"),
                                resultSet.getString("data_type"),
                                "YES".equals(resultSet.getString("is_nullable")),
                                (Integer) resultSet.getObject("character_maximum_length"),
                                resultSet.getString("column_default")))
                .stream()
                .collect(Collectors.toMap(ColumnDefinition::key, column -> column));

        assertEquals(25, columns.size());
        assertColumn(columns, "identity_user_account", "id", "uuid", false, null);
        assertColumn(columns, "identity_user_account", "login", "character varying", false, 255);
        assertColumn(columns, "identity_user_account", "display_name", "character varying", false, 255);
        assertColumn(columns, "identity_user_account", "avatar_url", "character varying", true, 2048);
        assertColumn(columns, "identity_user_account", "status", "character varying", false, 16);
        assertColumn(columns, "identity_user_account", "created_at", "timestamp with time zone", false, null);
        assertColumn(columns, "identity_user_account", "updated_at", "timestamp with time zone", false, null);
        assertColumn(columns, "identity_user_account", "last_login_at", "timestamp with time zone", false, null);
        assertColumn(columns, "identity_user_account", "version", "bigint", false, null);
        assertTrue(columns.get("identity_user_account.version").defaultValue().contains("0"));

        assertColumn(columns, "identity_external_identity", "id", "uuid", false, null);
        assertColumn(columns, "identity_external_identity", "user_id", "uuid", false, null);
        assertColumn(columns, "identity_external_identity", "provider", "character varying", false, 32);
        assertColumn(columns, "identity_external_identity", "provider_subject", "character varying", false, 64);
        assertColumn(columns, "identity_external_identity", "created_at", "timestamp with time zone", false, null);
        assertColumn(columns, "identity_external_identity", "updated_at", "timestamp with time zone", false, null);

        assertColumn(columns, "spring_session", "primary_id", "character", false, 36);
        assertColumn(columns, "spring_session", "session_id", "character", false, 36);
        assertColumn(columns, "spring_session", "creation_time", "bigint", false, null);
        assertColumn(columns, "spring_session", "last_access_time", "bigint", false, null);
        assertColumn(columns, "spring_session", "max_inactive_interval", "integer", false, null);
        assertColumn(columns, "spring_session", "expiry_time", "bigint", false, null);
        assertColumn(columns, "spring_session", "principal_name", "character varying", true, 100);

        assertColumn(columns, "spring_session_attributes", "session_primary_id", "character", false, 36);
        assertColumn(columns, "spring_session_attributes", "attribute_name", "character varying", false, 200);
        assertColumn(columns, "spring_session_attributes", "attribute_bytes", "bytea", false, null);
    }

    @Test
    void createsStableIdentityAndSessionConstraints() {
        Map<String, String> constraints = jdbcTemplate.query(
                        """
                        select constraint_name, pg_get_constraintdef(pg_constraint.oid) as definition
                        from information_schema.table_constraints
                        join pg_constraint on pg_constraint.conname = constraint_name
                        join pg_class on pg_class.oid = pg_constraint.conrelid
                                     and pg_class.relname = table_name
                        where table_schema = current_schema()
                          and table_name in (
                              'identity_user_account',
                              'identity_external_identity',
                              'spring_session',
                              'spring_session_attributes')
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("constraint_name"),
                                resultSet.getString("definition")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(Set.of(
                "pk_identity_user_account",
                "ck_identity_user_login_not_blank",
                "ck_identity_user_display_name_not_blank",
                "ck_identity_user_status",
                "ck_identity_user_version",
                "pk_identity_external_identity",
                "fk_identity_external_user",
                "uk_identity_external_provider_subject",
                "uk_identity_external_user_provider",
                "ck_identity_external_provider",
                "ck_identity_external_subject_not_blank",
                "spring_session_pk",
                "spring_session_attributes_pk",
                "spring_session_attributes_fk"), constraints.keySet());

        assertContains(constraints, "ck_identity_user_status", "ACTIVE", "DISABLED");
        assertContains(constraints, "ck_identity_external_provider", "github");
        assertContains(constraints, "uk_identity_external_provider_subject", "provider", "provider_subject");
        assertContains(constraints, "uk_identity_external_user_provider", "user_id", "provider");
        assertContains(constraints, "fk_identity_external_user", "identity_user_account", "ON DELETE CASCADE");
        assertContains(constraints, "spring_session_attributes_fk", "spring_session", "ON DELETE CASCADE");
    }

    @Test
    void createsOfficialSpringSessionIndexes() {
        Map<String, String> indexes = jdbcTemplate.query(
                        """
                        select indexname, indexdef
                        from pg_indexes
                        where schemaname = current_schema()
                          and tablename = 'spring_session'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("indexname"),
                                resultSet.getString("indexdef")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(Set.of(
                "spring_session_pk",
                "spring_session_ix1",
                "spring_session_ix2",
                "spring_session_ix3"), indexes.keySet());
        assertContains(indexes, "spring_session_ix1", "session_id");
        assertContains(indexes, "spring_session_ix2", "expiry_time");
        assertContains(indexes, "spring_session_ix3", "principal_name");
    }

    @Test
    void rejectsDuplicateProviderSubject() {
        UUID firstUser = insertUser("first-provider-subject", "ACTIVE");
        UUID secondUser = insertUser("second-provider-subject", "ACTIVE");
        insertIdentity(firstUser, "github", "10001");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdentity(secondUser, "github", "10001"));
    }

    @Test
    void rejectsMultipleGithubBindingsForOneUser() {
        UUID userId = insertUser("duplicate-provider", "ACTIVE");
        insertIdentity(userId, "github", "10002");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdentity(userId, "github", "10003"));
    }

    @Test
    void rejectsUnknownAccountStatus() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertUser("unknown-status", "UNKNOWN"));
    }

    @Test
    void rejectsProvidersOutsideTheApprovedGithubContract() {
        UUID userId = insertUser("unknown-provider", "ACTIVE");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdentity(userId, "gitlab", "10004"));
    }

    @Test
    void rejectsIdentityForUnknownUser() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdentity(UUID.randomUUID(), "github", "10005"));
    }

    @Test
    void rejectsAttributeForUnknownSession() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        insert into spring_session_attributes (
                            session_primary_id, attribute_name, attribute_bytes)
                        values (?, ?, ?)
                        """,
                        UUID.randomUUID().toString(),
                        "security-context",
                        new byte[] {1}));
    }

    @Test
    void deletingSessionCascadesToAttributes() {
        String primaryId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                insert into spring_session (
                    primary_id, session_id, creation_time, last_access_time,
                    max_inactive_interval, expiry_time, principal_name)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                primaryId,
                sessionId,
                1L,
                1L,
                3600,
                3600001L,
                null);
        jdbcTemplate.update(
                """
                insert into spring_session_attributes (
                    session_primary_id, attribute_name, attribute_bytes)
                values (?, ?, ?)
                """,
                primaryId,
                "security-context",
                new byte[] {1});

        jdbcTemplate.update("delete from spring_session where primary_id = ?", primaryId);

        Integer remainingAttributes = jdbcTemplate.queryForObject(
                "select count(*) from spring_session_attributes where session_primary_id = ?",
                Integer.class,
                primaryId);
        assertEquals(0, remainingAttributes);
    }

    private UUID insertUser(String login, String status) {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into identity_user_account (
                    id, login, display_name, avatar_url, status,
                    created_at, updated_at, last_login_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                login,
                "Test User",
                null,
                status,
                now,
                now,
                now);
        return userId;
    }

    private void insertIdentity(UUID userId, String provider, String providerSubject) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into identity_external_identity (
                    id, user_id, provider, provider_subject, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                provider,
                providerSubject,
                now,
                now);
    }

    private static void assertColumn(
            Map<String, ColumnDefinition> columns,
            String table,
            String name,
            String type,
            boolean nullable,
            Integer maximumLength) {
        ColumnDefinition actual = columns.get(table + "." + name);
        assertNotNull(actual, table + "." + name + " must exist");
        assertEquals(
                new ColumnDefinition(table, name, type, nullable, maximumLength, null).withoutDefault(),
                actual.withoutDefault());
    }

    private static void assertContains(Map<String, String> definitions, String name, String... fragments) {
        String definition = definitions.get(name);
        for (String fragment : fragments) {
            assertTrue(definition.contains(fragment), name + " must contain " + fragment + ": " + definition);
        }
    }

    private record ColumnDefinition(
            String table,
            String name,
            String type,
            boolean nullable,
            Integer maximumLength,
            String defaultValue) {

        String key() {
            return table + "." + name;
        }

        ColumnDefinition withoutDefault() {
            return new ColumnDefinition(table, name, type, nullable, maximumLength, null);
        }
    }
}
