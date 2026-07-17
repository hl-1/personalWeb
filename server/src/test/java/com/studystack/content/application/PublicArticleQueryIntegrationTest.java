package com.studystack.content.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.content.domain.Article;
import com.studystack.content.domain.ArticleRepository;
import com.studystack.content.domain.Category;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.Tag;
import com.studystack.content.domain.TagRepository;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
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
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=off"
})
class PublicArticleQueryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    PublicArticleQuery query;

    @Autowired
    ArticleRepository articles;

    @Autowired
    CategoryRepository categories;

    @Autowired
    TagRepository tags;

    @Autowired
    SlugPolicy slugPolicy;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearContentData() {
        jdbcTemplate.execute(
                "truncate table content_article_tag, content_article, content_category, content_tag cascade");
        statistics().clear();
    }

    @Test
    void listsPublicArticlesWithDefaultsAndStableUuidTieBreaking() {
        Instant currentTime = now();
        Instant tiedPublication = currentTime.minusSeconds(120);
        Category java = newCategory("java", currentTime);
        Tag spring = newTag("spring", currentTime);
        Article tiedLow = publishedArticle(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "tied-low",
                java,
                tiedPublication,
                spring);
        Article tiedHigh = publishedArticle(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "tied-high",
                java,
                tiedPublication,
                spring);
        Article older = publishedArticle(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "older",
                java,
                currentTime.minusSeconds(240),
                spring);
        persist(java, List.of(spring), List.of(tiedLow, tiedHigh, older));

        Page<ArticleSummary> result = query.findArticles(null, null, null, null);

        assertEquals(0, result.getNumber());
        assertEquals(10, result.getSize());
        assertEquals(3, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(
                List.of("tied-high", "tied-low", "older"),
                result.getContent().stream().map(ArticleSummary::slug).toList());
        assertEquals("java", result.getContent().getFirst().category());
        assertEquals(List.of("spring"), result.getContent().getFirst().tags());
    }

    @Test
    void validatesPaginationAndAcceptsTheMaximumPageSize() {
        Page<ArticleSummary> maximum = query.findArticles(0, 50, null, null);

        assertEquals(0, maximum.getNumber());
        assertEquals(50, maximum.getSize());
        assertTrue(maximum.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> query.findArticles(-1, 10, null, null));
        assertThrows(IllegalArgumentException.class, () -> query.findArticles(0, -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> query.findArticles(0, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> query.findArticles(0, 51, null, null));
    }

    @Test
    void combinesNormalizedCategoryAndTagFilters() {
        Instant currentTime = now();
        Category java = newCategory("java", currentTime);
        Category architecture = newCategory("architecture", currentTime);
        Tag spring = newTag("spring", currentTime);
        Tag database = newTag("database", currentTime);
        Article match = publishedArticle(
                UUID.randomUUID(), "java-spring", java, currentTime.minusSeconds(60), spring);
        Article wrongTag = publishedArticle(
                UUID.randomUUID(), "java-database", java, currentTime.minusSeconds(90), database);
        Article wrongCategory = publishedArticle(
                UUID.randomUUID(), "architecture-spring", architecture, currentTime.minusSeconds(120), spring);
        persist(
                List.of(java, architecture),
                List.of(spring, database),
                List.of(match, wrongTag, wrongCategory));

        Page<ArticleSummary> result = query.findArticles(0, 10, "  JAVA  ", " SPRING ");

        assertEquals(List.of("java-spring"),
                result.getContent().stream().map(ArticleSummary::slug).toList());
        assertEquals(1, result.getTotalElements());
        assertThrows(IllegalArgumentException.class,
                () -> query.findArticles(0, 10, "invalid/category", null));
        assertThrows(IllegalArgumentException.class,
                () -> query.findArticles(0, 10, null, "非法标签"));
    }

    @Test
    void returnsOnlyCurrentPublishedDetailAsSanitizedHtml() {
        Instant currentTime = now();
        Category java = newCategory("java", currentTime);
        Tag spring = newTag("spring", currentTime);
        Article visible = article(
                UUID.randomUUID(),
                "visible-detail",
                java,
                "# Safe\n\n<script>alert(1)</script>\n\n[site](https://example.com)",
                currentTime.minusSeconds(60));
        visible.addTag(spring, currentTime.minusSeconds(61));
        visible.publish(currentTime.minusSeconds(60));
        Article draft = article(
                UUID.randomUUID(), "draft-hidden", java, "draft secret", currentTime.minusSeconds(60));
        Article future = publishedArticle(
                UUID.randomUUID(), "future-hidden", java, currentTime.plusSeconds(3600), spring);
        Article archived = publishedArticle(
                UUID.randomUUID(), "archived-hidden", java, currentTime.minusSeconds(120), spring);
        archived.archive(currentTime.minusSeconds(30));
        persist(java, List.of(spring), List.of(visible, draft, future, archived));

        ArticleDetail detail = query.findArticle("  VISIBLE-DETAIL  ");

        assertEquals("visible-detail", detail.slug());
        assertEquals("/blog/visible-detail", detail.canonicalPath());
        assertTrue(detail.contentHtml().contains("<h1>Safe</h1>"));
        assertTrue(detail.contentHtml().contains("rel=\"nofollow noopener noreferrer\""));
        assertFalse(detail.contentHtml().contains("script"));
        assertEquals("java", detail.category());
        assertEquals(List.of("spring"), detail.tags());
        assertNotFound("draft-hidden");
        assertNotFound("future-hidden");
        assertNotFound("archived-hidden");
        assertNotFound("missing-article");
        assertNotFound("INVALID/SLUG");
    }

    @Test
    void applicationDtosExposeNoEntityStateOrRawMarkdown() {
        Set<String> summaryFields = recordComponents(ArticleSummary.class);
        Set<String> detailFields = recordComponents(ArticleDetail.class);

        assertEquals(Set.of(
                        "id", "slug", "title", "summary", "category", "tags", "publishedAt", "updatedAt"),
                summaryFields);
        assertEquals(Set.of(
                        "id", "slug", "title", "summary", "category", "tags", "publishedAt", "updatedAt",
                        "contentHtml", "seoTitle", "seoDescription", "canonicalPath"),
                detailFields);
        assertFalse(detailFields.contains("bodyMarkdown"));
        assertFalse(detailFields.contains("status"));
        assertFalse(detailFields.contains("version"));
    }

    @Test
    void listsOnlyTaxonomiesUsedByCurrentPublishedArticles() {
        Instant currentTime = now();
        Category java = newCategory("java", currentTime);
        Category architecture = newCategory("architecture", currentTime);
        Category emptyCategory = newCategory("empty-category", currentTime);
        Tag spring = newTag("spring", currentTime);
        Tag database = newTag("database", currentTime);
        Tag emptyTag = newTag("empty-tag", currentTime);
        Article javaArticle = publishedArticle(
                UUID.randomUUID(), "java-public", java, currentTime.minusSeconds(60), spring);
        javaArticle.addTag(database, currentTime.minusSeconds(61));
        Article architectureArticle = publishedArticle(
                UUID.randomUUID(), "architecture-public", architecture, currentTime.minusSeconds(90), spring);
        Article draft = article(
                UUID.randomUUID(), "draft-hidden", java, "draft", currentTime.minusSeconds(30));
        draft.addTag(database, currentTime.minusSeconds(30));
        Article future = publishedArticle(
                UUID.randomUUID(), "future-hidden", java, currentTime.plusSeconds(3600), database);
        persist(
                List.of(java, architecture, emptyCategory),
                List.of(spring, database, emptyTag),
                List.of(javaArticle, architectureArticle, draft, future));

        assertEquals(
                List.of("architecture:1", "java:1"),
                query.findCategories().stream()
                        .map(item -> item.slug() + ":" + item.publishedArticleCount())
                        .toList());
        assertEquals(
                List.of("database:1", "spring:2"),
                query.findTags().stream()
                        .map(item -> item.slug() + ":" + item.publishedArticleCount())
                        .toList());
    }

    @Test
    void mapsAFullPageWithinAConstantQueryLimit() {
        Instant currentTime = now();
        Category java = newCategory("java", currentTime);
        Tag spring = newTag("spring", currentTime);
        List<Article> page = IntStream.range(0, 10)
                .mapToObj(index -> publishedArticle(
                        new UUID(0, index + 1L),
                        "bounded-" + index,
                        java,
                        currentTime.minusSeconds(index + 1L),
                        spring))
                .toList();
        persist(java, List.of(spring), page);
        Statistics statistics = statistics();
        statistics.clear();

        Page<ArticleSummary> result = query.findArticles(0, 10, null, null);

        assertEquals(10, result.getNumberOfElements());
        assertTrue(
                statistics.getPrepareStatementCount() <= 3,
                () -> "expected at most 3 statements for a full page but got "
                        + statistics.getPrepareStatementCount());
    }

    private void assertNotFound(String slug) {
        ArticleNotFoundException exception = assertThrows(
                ArticleNotFoundException.class,
                () -> query.findArticle(slug));
        assertEquals(ArticleNotFoundException.CODE, exception.code());
        assertFalse(exception.getMessage().contains(slug));
    }

    private Set<String> recordComponents(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private Article publishedArticle(
            UUID id,
            String slug,
            Category category,
            Instant publishedAt,
            Tag tag) {
        Article article = article(id, slug, category, "Body " + slug, publishedAt.minusSeconds(1));
        article.addTag(tag, publishedAt.minusSeconds(1));
        article.publish(publishedAt);
        return article;
    }

    private Article article(
            UUID id,
            String slug,
            Category category,
            String bodyMarkdown,
            Instant timestamp) {
        return new Article(
                id,
                slugPolicy.create(slug),
                "Title " + slug,
                "Summary " + slug,
                bodyMarkdown,
                category,
                "SEO " + slug,
                "SEO description " + slug,
                timestamp);
    }

    private Category newCategory(String slug, Instant timestamp) {
        return new Category(
                UUID.randomUUID(),
                "Category " + slug,
                slugPolicy.create(slug),
                timestamp);
    }

    private Tag newTag(String slug, Instant timestamp) {
        return new Tag(
                UUID.randomUUID(),
                "Tag " + slug,
                slugPolicy.create(slug),
                timestamp);
    }

    private void persist(Category category, List<Tag> tagValues, List<Article> articleValues) {
        persist(List.of(category), tagValues, articleValues);
    }

    private void persist(
            List<Category> categoryValues,
            List<Tag> tagValues,
            List<Article> articleValues) {
        inTransaction(() -> {
            categoryValues.forEach(categories::save);
            tagValues.forEach(tags::save);
            articleValues.forEach(articles::save);
            entityManager.flush();
            entityManager.clear();
        });
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

}
