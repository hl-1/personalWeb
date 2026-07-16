package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.StudyStackApplication;
import java.io.IOException;
import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
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
class PostgresFlywayIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Environment environment;

    @Test
    void usesPostgreSqlWithoutAnInMemoryFallback() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    void appliesFoundationAndIdentityMigrationsInOrder() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertEquals(List.of("1", "2"), appliedVersions);
    }

    @Test
    void keepsHibernateInSchemaValidationMode() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test
    void createsOnlyTheApprovedFoundationAndIdentityTables() {
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
    void failsStartupWhenPostgreSqlCannotBeReached() {
        SpringApplication application = new SpringApplication(StudyStackApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("test");
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", "jdbc:postgresql://127.0.0.1:1/unreachable",
                "spring.datasource.username", "studystack",
                "spring.datasource.password", "not-a-secret",
                "spring.datasource.hikari.connection-timeout", "250",
                "spring.datasource.hikari.initialization-fail-timeout", "250",
                "spring.flyway.connect-retries", "0",
                "springdoc.api-docs.enabled", "false",
                "springdoc.swagger-ui.enabled", "false"));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            try (var ignored = application.run()) {
                // A successful context is closed before assertThrows reports the missing failure.
            }
        });

        assertTrue(causeChainContains(failure, "org.postgresql.util.PSQLException"),
                "startup failure must retain the PostgreSQL connection root cause");
    }

    @Test
    void rejectsModifiedAppliedMigrationChecksum(@TempDir Path migrationDirectory) throws IOException {
        String schema = "checksum_mismatch_case";
        Path sourceMigration = Path.of("src", "main", "resources", "db", "migration", "V1__baseline.sql");
        Path copiedMigration = migrationDirectory.resolve("V1__baseline.sql");
        String baselineSql = Files.readString(sourceMigration);
        Files.writeString(copiedMigration, baselineSql);

        try {
            isolatedFlyway(schema, migrationDirectory).migrate();
            Files.writeString(copiedMigration, baselineSql + System.lineSeparator() + "SELECT 2;" + System.lineSeparator());

            FlywayException failure = assertThrows(
                    FlywayException.class,
                    () -> isolatedFlyway(schema, migrationDirectory).validate());

            assertTrue(failure.getMessage().toLowerCase().contains("checksum mismatch"),
                    "Flyway must explain that the applied migration checksum changed");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private static Flyway isolatedFlyway(String schema, Path migrationDirectory) {
        String location = "filesystem:" + migrationDirectory.toAbsolutePath().toString().replace('\\', '/');
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .locations(location)
                .load();
    }

    private static boolean causeChainContains(Throwable failure, String className) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getClass().getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
