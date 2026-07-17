package com.studystack.portfolio.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
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
class PortfolioSchemaIntegrationTest {

    private static final List<String> PORTFOLIO_TABLES = List.of(
            "portfolio_experience",
            "portfolio_profile",
            "portfolio_project",
            "portfolio_skill");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appliesPortfolioMigrationAfterContent() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertTrue(appliedVersions.size() >= 4, "V4 portfolio migration must be applied");
        assertEquals(List.of("1", "2", "3", "4"), appliedVersions.subList(0, 4));
    }

    @Test
    void createsExactlyTheApprovedPortfolioTables() {
        assertEquals(PORTFOLIO_TABLES, portfolioTables());
    }

    @Test
    void createsTheApprovedPortfolioColumnContract() {
        requirePortfolioSchema();
        Map<String, ColumnDefinition> columns = portfolioColumns();

        assertEquals(42, columns.size());
        assertColumn(columns, "portfolio_profile", "id", "integer", false, null);
        assertColumn(columns, "portfolio_profile", "display_name", "character varying", false, 120);
        assertColumn(columns, "portfolio_profile", "headline", "character varying", false, 180);
        assertColumn(columns, "portfolio_profile", "bio_markdown", "character varying", false, 50_000);
        assertColumn(columns, "portfolio_profile", "seo_description", "character varying", true, 160);
        assertAuditColumns(columns, "portfolio_profile");

        assertColumn(columns, "portfolio_project", "id", "uuid", false, null);
        assertColumn(columns, "portfolio_project", "slug", "character varying", false, 120);
        assertColumn(columns, "portfolio_project", "title", "character varying", false, 180);
        assertColumn(columns, "portfolio_project", "summary", "character varying", false, 500);
        assertColumn(columns, "portfolio_project", "description_markdown", "character varying", false, 100_000);
        assertColumn(columns, "portfolio_project", "project_url", "character varying", true, 2_048);
        assertColumn(columns, "portfolio_project", "repository_url", "character varying", true, 2_048);
        assertColumn(columns, "portfolio_project", "status", "character varying", false, 16);
        assertColumn(columns, "portfolio_project", "featured", "boolean", false, null);
        assertColumn(columns, "portfolio_project", "sort_order", "integer", false, null);
        assertColumn(columns, "portfolio_project", "published_at", "timestamp with time zone", true, null);
        assertAuditColumns(columns, "portfolio_project");
        assertTrue(columns.get("portfolio_project.featured").defaultValue().contains("false"));
        assertTrue(columns.get("portfolio_project.sort_order").defaultValue().contains("0"));

        assertColumn(columns, "portfolio_skill", "id", "uuid", false, null);
        assertColumn(columns, "portfolio_skill", "name", "character varying", false, 120);
        assertColumn(columns, "portfolio_skill", "category", "character varying", false, 120);
        assertColumn(columns, "portfolio_skill", "summary", "character varying", true, 500);
        assertColumn(columns, "portfolio_skill", "sort_order", "integer", false, null);
        assertColumn(columns, "portfolio_skill", "visible", "boolean", false, null);
        assertAuditColumns(columns, "portfolio_skill");
        assertTrue(columns.get("portfolio_skill.sort_order").defaultValue().contains("0"));
        assertTrue(columns.get("portfolio_skill.visible").defaultValue().contains("true"));

        assertColumn(columns, "portfolio_experience", "id", "uuid", false, null);
        assertColumn(columns, "portfolio_experience", "organization", "character varying", false, 180);
        assertColumn(columns, "portfolio_experience", "role", "character varying", false, 180);
        assertColumn(columns, "portfolio_experience", "start_date", "date", false, null);
        assertColumn(columns, "portfolio_experience", "end_date", "date", true, null);
        assertColumn(columns, "portfolio_experience", "summary_markdown", "character varying", false, 20_000);
        assertColumn(columns, "portfolio_experience", "sort_order", "integer", false, null);
        assertColumn(columns, "portfolio_experience", "visible", "boolean", false, null);
        assertAuditColumns(columns, "portfolio_experience");
        assertTrue(columns.get("portfolio_experience.sort_order").defaultValue().contains("0"));
        assertTrue(columns.get("portfolio_experience.visible").defaultValue().contains("true"));
    }

    @Test
    void createsStablePortfolioConstraintsWithoutForeignKeys() {
        requirePortfolioSchema();
        Map<String, String> constraints = portfolioConstraints();

        assertEquals(Set.of(
                "pk_portfolio_profile",
                "ck_portfolio_profile_singleton",
                "ck_portfolio_profile_display_name_not_blank",
                "ck_portfolio_profile_headline_not_blank",
                "ck_portfolio_profile_version",
                "pk_portfolio_project",
                "uk_portfolio_project_slug",
                "ck_portfolio_project_slug",
                "ck_portfolio_project_title_not_blank",
                "ck_portfolio_project_summary_not_blank",
                "ck_portfolio_project_status",
                "ck_portfolio_project_publication",
                "ck_portfolio_project_project_url",
                "ck_portfolio_project_repository_url",
                "ck_portfolio_project_sort_order",
                "ck_portfolio_project_version",
                "pk_portfolio_skill",
                "ck_portfolio_skill_name_not_blank",
                "ck_portfolio_skill_category_not_blank",
                "ck_portfolio_skill_sort_order",
                "ck_portfolio_skill_version",
                "pk_portfolio_experience",
                "ck_portfolio_experience_organization_not_blank",
                "ck_portfolio_experience_role_not_blank",
                "ck_portfolio_experience_date_range",
                "ck_portfolio_experience_sort_order",
                "ck_portfolio_experience_version"), constraints.keySet());

        assertContains(constraints, "ck_portfolio_profile_singleton", "id = 1");
        assertContains(constraints, "ck_portfolio_project_status", "DRAFT", "PUBLISHED", "ARCHIVED");
        assertContains(constraints, "ck_portfolio_project_publication", "PUBLISHED", "published_at");
        assertContains(constraints, "ck_portfolio_project_project_url", "https://");
        assertContains(constraints, "ck_portfolio_project_repository_url", "https://");
        assertContains(constraints, "ck_portfolio_experience_date_range", "end_date", "start_date");
        assertTrue(constraints.keySet().stream().noneMatch(name -> name.startsWith("fk_")));
    }

    @Test
    void createsExactPublicPortfolioIndexes() {
        requirePortfolioSchema();
        Map<String, String> indexes = portfolioIndexes();

        assertEquals(Set.of(
                "ix_portfolio_project_publication",
                "ix_portfolio_skill_visible_order",
                "ix_portfolio_experience_visible_order"), indexes.keySet());
        assertContains(
                indexes,
                "ix_portfolio_project_publication",
                "status",
                "published_at DESC",
                "id DESC");
        assertContains(
                indexes,
                "ix_portfolio_skill_visible_order",
                "visible",
                "sort_order",
                "id");
        assertContains(
                indexes,
                "ix_portfolio_experience_visible_order",
                "visible",
                "sort_order",
                "start_date DESC",
                "id");
    }

    @Test
    void enforcesTheSingletonProfile() {
        requirePortfolioSchema();
        insertProfile(1, ProfileText.defaults());

        assertThrows(DataIntegrityViolationException.class, () -> insertProfile(1, ProfileText.defaults()));
        assertThrows(DataIntegrityViolationException.class, () -> insertProfile(2, ProfileText.defaults()));
    }

    @Test
    void rejectsDuplicateProjectSlugUnknownStatusAndPublishedProjectWithoutTimestamp() {
        requirePortfolioSchema();
        insertProject("duplicate-project", "DRAFT", null, 0, null, null, ProjectText.defaults());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject(
                        "duplicate-project", "DRAFT", null, 0, null, null, ProjectText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject("unknown-status", "UNKNOWN", null, 0, null, null, ProjectText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject("missing-time", "PUBLISHED", null, 0, null, null, ProjectText.defaults()));
    }

    @Test
    void rejectsInvalidProjectUrlsAndNegativeSortOrder() {
        requirePortfolioSchema();
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject(
                        "http-project",
                        "DRAFT",
                        null,
                        0,
                        "http://example.com",
                        null,
                        ProjectText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject(
                        "relative-repository",
                        "DRAFT",
                        null,
                        0,
                        null,
                        "/repository",
                        ProjectText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject(
                        "credential-project",
                        "DRAFT",
                        null,
                        0,
                        "https://user:secret@example.com/project",
                        null,
                        ProjectText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject(
                        "credential-repository",
                        "DRAFT",
                        null,
                        0,
                        null,
                        "https://user:secret@example.com/repository",
                        ProjectText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertProject(
                        "negative-project-order",
                        "DRAFT",
                        null,
                        -1,
                        null,
                        null,
                        ProjectText.defaults()));
        assertThrows(DataIntegrityViolationException.class, () -> insertSkill(-1));
        assertThrows(DataIntegrityViolationException.class, () -> insertExperience(
                LocalDate.of(2024, 1, 1), null, -1, ExperienceText.defaults()));
    }

    @Test
    void rejectsExperienceEndingBeforeItStarts() {
        requirePortfolioSchema();
        assertThrows(DataIntegrityViolationException.class, () -> insertExperience(
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 1, 31),
                0,
                ExperienceText.defaults()));
    }

    @Test
    void rejectsPortfolioTextBeyondContractLimits() {
        requirePortfolioSchema();
        assertThrows(DataIntegrityViolationException.class, () -> insertProfile(
                1, new ProfileText("Name", "Headline", "b".repeat(50_001), null)));
        assertThrows(DataIntegrityViolationException.class, () -> insertProject(
                "long-project-title",
                "DRAFT",
                null,
                0,
                null,
                null,
                new ProjectText("t".repeat(181), "Summary", "Description")));
        assertThrows(DataIntegrityViolationException.class, () -> insertProject(
                "long-project-summary",
                "DRAFT",
                null,
                0,
                null,
                null,
                new ProjectText("Title", "s".repeat(501), "Description")));
        assertThrows(DataIntegrityViolationException.class, () -> insertProject(
                "long-project-description",
                "DRAFT",
                null,
                0,
                null,
                null,
                new ProjectText("Title", "Summary", "d".repeat(100_001))));
        assertThrows(DataIntegrityViolationException.class, () -> insertExperience(
                LocalDate.of(2024, 1, 1),
                null,
                0,
                new ExperienceText("Organization", "Role", "s".repeat(20_001))));
    }

    private List<String> portfolioTables() {
        return jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name like 'portfolio_%'
                order by table_name
                """,
                String.class);
    }

    private Map<String, ColumnDefinition> portfolioColumns() {
        return jdbcTemplate.query(
                        """
                        select table_name, column_name, data_type, is_nullable,
                               character_maximum_length, column_default
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name like 'portfolio_%'
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
    }

    private Map<String, String> portfolioConstraints() {
        return jdbcTemplate.query(
                        """
                        select constraint_name, pg_get_constraintdef(pg_constraint.oid) as definition
                        from information_schema.table_constraints
                        join pg_constraint on pg_constraint.conname = constraint_name
                        join pg_class on pg_class.oid = pg_constraint.conrelid
                                     and pg_class.relname = table_name
                        where table_schema = current_schema()
                          and table_name like 'portfolio_%'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("constraint_name"),
                                resultSet.getString("definition")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> portfolioIndexes() {
        return jdbcTemplate.query(
                        """
                        select indexname, indexdef
                        from pg_indexes
                        where schemaname = current_schema()
                          and indexname like 'ix_portfolio_%'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("indexname"),
                                resultSet.getString("indexdef")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void requirePortfolioSchema() {
        Assumptions.assumeTrue(
                portfolioTables().equals(PORTFOLIO_TABLES),
                "V4 portfolio tables must exist before detailed contract checks");
    }

    private void insertProfile(int id, ProfileText text) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into portfolio_profile (
                    id, display_name, headline, bio_markdown, seo_description, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                text.displayName(),
                text.headline(),
                text.bioMarkdown(),
                text.seoDescription(),
                now,
                now);
    }

    private UUID insertProject(
            String slug,
            String status,
            Instant publishedAt,
            int sortOrder,
            String projectUrl,
            String repositoryUrl,
            ProjectText text) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into portfolio_project (
                    id, slug, title, summary, description_markdown, project_url, repository_url,
                    status, featured, sort_order, published_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?, ?, ?)
                """,
                id,
                slug,
                text.title(),
                text.summary(),
                text.descriptionMarkdown(),
                projectUrl,
                repositoryUrl,
                status,
                sortOrder,
                publishedAt == null ? null : Timestamp.from(publishedAt),
                now,
                now);
        return id;
    }

    private UUID insertSkill(int sortOrder) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into portfolio_skill (
                    id, name, category, summary, sort_order, visible, created_at, updated_at)
                values (?, ?, ?, ?, ?, true, ?, ?)
                """,
                id,
                "Java",
                "Backend",
                "Summary",
                sortOrder,
                now,
                now);
        return id;
    }

    private UUID insertExperience(
            LocalDate startDate,
            LocalDate endDate,
            int sortOrder,
            ExperienceText text) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into portfolio_experience (
                    id, organization, role, start_date, end_date, summary_markdown,
                    sort_order, visible, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                """,
                id,
                text.organization(),
                text.role(),
                Date.valueOf(startDate),
                endDate == null ? null : Date.valueOf(endDate),
                text.summaryMarkdown(),
                sortOrder,
                now,
                now);
        return id;
    }

    private static void assertAuditColumns(Map<String, ColumnDefinition> columns, String table) {
        assertColumn(columns, table, "created_at", "timestamp with time zone", false, null);
        assertColumn(columns, table, "updated_at", "timestamp with time zone", false, null);
        assertColumn(columns, table, "version", "bigint", false, null);
        assertTrue(columns.get(table + ".version").defaultValue().contains("0"));
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
        assertNotNull(definition, name + " must exist");
        for (String fragment : fragments) {
            assertTrue(definition.contains(fragment), name + " must contain " + fragment + ": " + definition);
        }
    }

    private record ProfileText(
            String displayName,
            String headline,
            String bioMarkdown,
            String seoDescription) {

        static ProfileText defaults() {
            return new ProfileText("Display name", "Headline", "Bio", null);
        }
    }

    private record ProjectText(
            String title,
            String summary,
            String descriptionMarkdown) {

        static ProjectText defaults() {
            return new ProjectText("Title", "Summary", "Description");
        }
    }

    private record ExperienceText(
            String organization,
            String role,
            String summaryMarkdown) {

        static ExperienceText defaults() {
            return new ExperienceText("Organization", "Role", "Summary");
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
