package com.studystack.content.infrastructure;

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
class ContentSchemaIntegrationTest {

    private static final List<String> CONTENT_TABLES = List.of(
            "content_article",
            "content_article_tag",
            "content_category",
            "content_tag");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appliesContentMigrationAfterFoundationAndIdentity() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertTrue(appliedVersions.size() >= 3, "V3 content migration must be applied");
        assertEquals(List.of("1", "2", "3"), appliedVersions.subList(0, 3));
    }

    @Test
    void createsExactlyTheApprovedContentTables() {
        assertEquals(CONTENT_TABLES, contentTables());
    }

    @Test
    void createsTheApprovedContentColumnContract() {
        requireContentSchema();
        Map<String, ColumnDefinition> columns = jdbcTemplate.query(
                        """
                        select table_name, column_name, data_type, is_nullable,
                               character_maximum_length, column_default
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name like 'content_%'
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

        assertEquals(27, columns.size());
        assertResourceColumns(columns, "content_category");
        assertResourceColumns(columns, "content_tag");

        assertColumn(columns, "content_article", "id", "uuid", false, null);
        assertColumn(columns, "content_article", "slug", "character varying", false, 120);
        assertColumn(columns, "content_article", "title", "character varying", false, 180);
        assertColumn(columns, "content_article", "summary", "character varying", false, 500);
        assertColumn(columns, "content_article", "body_markdown", "character varying", false, 200_000);
        assertColumn(columns, "content_article", "status", "character varying", false, 16);
        assertColumn(columns, "content_article", "category_id", "uuid", true, null);
        assertColumn(columns, "content_article", "seo_title", "character varying", true, 70);
        assertColumn(columns, "content_article", "seo_description", "character varying", true, 160);
        assertColumn(columns, "content_article", "published_at", "timestamp with time zone", true, null);
        assertColumn(columns, "content_article", "created_at", "timestamp with time zone", false, null);
        assertColumn(columns, "content_article", "updated_at", "timestamp with time zone", false, null);
        assertColumn(columns, "content_article", "version", "bigint", false, null);
        assertTrue(columns.get("content_article.version").defaultValue().contains("0"));

        assertColumn(columns, "content_article_tag", "article_id", "uuid", false, null);
        assertColumn(columns, "content_article_tag", "tag_id", "uuid", false, null);
    }

    @Test
    void createsStableContentConstraints() {
        requireContentSchema();
        Map<String, String> constraints = jdbcTemplate.query(
                        """
                        select constraint_name, pg_get_constraintdef(pg_constraint.oid) as definition
                        from information_schema.table_constraints
                        join pg_constraint on pg_constraint.conname = constraint_name
                        join pg_class on pg_class.oid = pg_constraint.conrelid
                                     and pg_class.relname = table_name
                        where table_schema = current_schema()
                          and table_name like 'content_%'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("constraint_name"),
                                resultSet.getString("definition")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(Set.of(
                "pk_content_category",
                "uk_content_category_name",
                "uk_content_category_slug",
                "ck_content_category_name_not_blank",
                "ck_content_category_slug",
                "ck_content_category_version",
                "pk_content_tag",
                "uk_content_tag_name",
                "uk_content_tag_slug",
                "ck_content_tag_name_not_blank",
                "ck_content_tag_slug",
                "ck_content_tag_version",
                "pk_content_article",
                "uk_content_article_slug",
                "ck_content_article_slug",
                "ck_content_article_title_not_blank",
                "ck_content_article_summary_not_blank",
                "ck_content_article_status",
                "ck_content_article_publication",
                "ck_content_article_version",
                "fk_content_article_category",
                "pk_content_article_tag",
                "fk_content_article_tag_article",
                "fk_content_article_tag_tag"), constraints.keySet());

        assertContains(constraints, "ck_content_article_status", "DRAFT", "PUBLISHED", "ARCHIVED");
        assertContains(constraints, "ck_content_article_publication", "PUBLISHED", "published_at");
        assertContains(constraints, "fk_content_article_category", "content_category", "ON DELETE SET NULL");
        assertContains(constraints, "fk_content_article_tag_article", "content_article", "ON DELETE CASCADE");
        assertContains(constraints, "fk_content_article_tag_tag", "content_tag", "ON DELETE CASCADE");
    }

    @Test
    void createsPublicListingAndFilterIndexes() {
        requireContentSchema();
        Map<String, String> indexes = jdbcTemplate.query(
                        """
                        select indexname, indexdef
                        from pg_indexes
                        where schemaname = current_schema()
                          and indexname like 'ix_content_%'
                        """,
                        (resultSet, rowNumber) -> Map.entry(
                                resultSet.getString("indexname"),
                                resultSet.getString("indexdef")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(Set.of(
                "ix_content_article_publication",
                "ix_content_article_category_publication",
                "ix_content_article_tag_tag_article"), indexes.keySet());
        assertContains(indexes, "ix_content_article_publication", "status", "published_at DESC", "id DESC");
        assertContains(
                indexes,
                "ix_content_article_category_publication",
                "category_id",
                "status",
                "published_at DESC",
                "id DESC");
        assertContains(indexes, "ix_content_article_tag_tag_article", "tag_id", "article_id");
    }

    @Test
    void rejectsDuplicateSlugsForEveryContentResource() {
        requireContentSchema();
        insertCategory("duplicate-category");
        assertThrows(DataIntegrityViolationException.class, () -> insertCategory("duplicate-category"));

        insertTag("duplicate-tag");
        assertThrows(DataIntegrityViolationException.class, () -> insertTag("duplicate-tag"));

        insertArticle("duplicate-article", "DRAFT", null, null, ArticleText.defaults());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticle("duplicate-article", "DRAFT", null, null, ArticleText.defaults()));
    }

    @Test
    void rejectsInvalidSlugsForEveryContentResource() {
        requireContentSchema();
        assertThrows(DataIntegrityViolationException.class, () -> insertCategory("Invalid Category"));
        assertThrows(DataIntegrityViolationException.class, () -> insertTag("-invalid-tag"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticle("ab", "DRAFT", null, null, ArticleText.defaults()));
    }

    @Test
    void rejectsArticleTextBeyondContractLimits() {
        requireContentSchema();
        assertArticleTextRejected("long-title", new ArticleText(
                "t".repeat(181), "summary", "body", null, null));
        assertArticleTextRejected("long-summary", new ArticleText(
                "title", "s".repeat(501), "body", null, null));
        assertArticleTextRejected("long-body", new ArticleText(
                "title", "summary", "b".repeat(200_001), null, null));
        assertArticleTextRejected("long-seo-title", new ArticleText(
                "title", "summary", "body", "s".repeat(71), null));
        assertArticleTextRejected("long-seo-description", new ArticleText(
                "title", "summary", "body", null, "s".repeat(161)));
    }

    @Test
    void rejectsUnknownStatusAndPublishedArticleWithoutTimestamp() {
        requireContentSchema();
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticle("unknown-status", "UNKNOWN", null, null, ArticleText.defaults()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticle("missing-published-at", "PUBLISHED", null, null, ArticleText.defaults()));

        UUID articleId = insertArticle(
                "published-with-time", "PUBLISHED", null, Instant.now(), ArticleText.defaults());
        assertNotNull(articleId);
    }

    @Test
    void rejectsArticleWithUnknownCategory() {
        requireContentSchema();
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticle(
                        "unknown-category",
                        "DRAFT",
                        UUID.randomUUID(),
                        null,
                        ArticleText.defaults()));
    }

    @Test
    void rejectsOrphanAndDuplicateArticleTagRelations() {
        requireContentSchema();
        UUID articleId = insertArticle("tag-relations", "DRAFT", null, null, ArticleText.defaults());
        UUID tagId = insertTag("relation-tag");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticleTag(UUID.randomUUID(), tagId));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticleTag(articleId, UUID.randomUUID()));

        insertArticleTag(articleId, tagId);
        assertThrows(DataIntegrityViolationException.class, () -> insertArticleTag(articleId, tagId));
    }

    private List<String> contentTables() {
        return jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name like 'content_%'
                order by table_name
                """,
                String.class);
    }

    private void requireContentSchema() {
        Assumptions.assumeTrue(
                contentTables().equals(CONTENT_TABLES),
                "V3 content tables must exist before detailed contract checks");
    }

    private UUID insertCategory(String slug) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into content_category (id, name, slug, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                id,
                slug,
                slug,
                now,
                now);
        return id;
    }

    private UUID insertTag(String slug) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into content_tag (id, name, slug, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                id,
                slug,
                slug,
                now,
                now);
        return id;
    }

    private UUID insertArticle(
            String slug,
            String status,
            UUID categoryId,
            Instant publishedAt,
            ArticleText text) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into content_article (
                    id, slug, title, summary, body_markdown, status, category_id,
                    seo_title, seo_description, published_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                slug,
                text.title(),
                text.summary(),
                text.bodyMarkdown(),
                status,
                categoryId,
                text.seoTitle(),
                text.seoDescription(),
                publishedAt == null ? null : Timestamp.from(publishedAt),
                now,
                now);
        return id;
    }

    private void insertArticleTag(UUID articleId, UUID tagId) {
        jdbcTemplate.update(
                "insert into content_article_tag (article_id, tag_id) values (?, ?)",
                articleId,
                tagId);
    }

    private void assertArticleTextRejected(String slug, ArticleText text) {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertArticle(slug, "DRAFT", null, null, text));
    }

    private static void assertResourceColumns(Map<String, ColumnDefinition> columns, String table) {
        assertColumn(columns, table, "id", "uuid", false, null);
        assertColumn(columns, table, "name", "character varying", false, 120);
        assertColumn(columns, table, "slug", "character varying", false, 120);
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

    private record ArticleText(
            String title,
            String summary,
            String bodyMarkdown,
            String seoTitle,
            String seoDescription) {

        static ArticleText defaults() {
            return new ArticleText("Title", "Summary", "Body", null, null);
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
