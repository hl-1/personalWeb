package com.studystack.content.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.content.domain.Article;
import com.studystack.content.domain.ArticleRepository;
import com.studystack.content.domain.ArticleStatus;
import com.studystack.content.domain.Category;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.Tag;
import com.studystack.content.domain.TagRepository;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ContentRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

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
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearContentData() {
        jdbcTemplate.execute(
                "truncate table content_article_tag, content_article, content_category, content_tag cascade");
    }

    @Test
    void persistsExactV3MappingWithUtcInstantsAndNullableCategory() {
        Instant timestamp = now();
        Article article = newArticle(
                UUID.randomUUID(), "mapping-article", null, timestamp);
        inTransaction(() -> {
            articles.save(article);
            entityManager.flush();
        });

        Article reloaded = inTransaction(() -> {
            Article found = articles.findById(article.id()).orElseThrow();
            assertTrue(found.tags().isEmpty());
            return found;
        });

        assertEquals(slugPolicy.create("mapping-article"), reloaded.slug());
        assertEquals(ArticleStatus.DRAFT, reloaded.status());
        assertEquals(timestamp, reloaded.createdAt());
        assertEquals(timestamp, reloaded.updatedAt());
        assertNull(reloaded.publishedAt());
        assertNull(reloaded.category());
        assertEquals(0L, reloaded.version());
        assertTrue(articles.findBySlug(article.slug()).isPresent());
    }

    @Test
    void persistsTaxonomyUtcVersionsAndFindsTaxonomyBySharedSlug() {
        Instant timestamp = now();
        Category category = newCategory("mapped-category", timestamp);
        Tag tag = newTag("mapped-tag", timestamp);

        inTransaction(() -> {
            categories.save(category);
            tags.save(tag);
            entityManager.flush();
        });

        Category reloadedCategory = categories.findBySlug(category.slug()).orElseThrow();
        Tag reloadedTag = tags.findBySlug(tag.slug()).orElseThrow();
        assertEquals(timestamp, reloadedCategory.createdAt());
        assertEquals(timestamp, reloadedCategory.updatedAt());
        assertEquals(0L, reloadedCategory.version());
        assertEquals(timestamp, reloadedTag.createdAt());
        assertEquals(timestamp, reloadedTag.updatedAt());
        assertEquals(0L, reloadedTag.version());
    }

    @Test
    void maintainsCategoryAndTagRelationshipsAndRejectsDuplicateTags() {
        Instant timestamp = now();
        Category category = newCategory("java", timestamp);
        Tag tag = newTag("spring", timestamp);
        Article article = newArticle(UUID.randomUUID(), "related-article", category, timestamp);

        article.addTag(tag, timestamp.plusSeconds(1));

        assertSame(category, article.category());
        assertTrue(category.articles().contains(article));
        assertTrue(tag.articles().contains(article));
        assertThrows(
                IllegalArgumentException.class,
                () -> article.addTag(tag, timestamp.plusSeconds(2)));
        assertThrows(UnsupportedOperationException.class, () -> article.tags().add(tag));
        assertThrows(UnsupportedOperationException.class, () -> category.articles().add(article));
        assertThrows(UnsupportedOperationException.class, () -> tag.articles().add(article));

        inTransaction(() -> {
            categories.save(category);
            tags.save(tag);
            articles.save(article);
            entityManager.flush();
        });

        Article reloaded = inTransaction(() -> {
            Article found = articles.findById(article.id()).orElseThrow();
            assertEquals(1, found.tags().size());
            assertEquals(category.id(), found.category().id());
            return found;
        });
        assertEquals(article.id(), reloaded.id());

        article.changeCategory(null, timestamp.plusSeconds(3));
        article.removeTag(tag, timestamp.plusSeconds(4));
        assertNull(article.category());
        assertFalse(category.articles().contains(article));
        assertTrue(article.tags().isEmpty());
        assertFalse(tag.articles().contains(article));
    }

    @Test
    void allowsDraftSlugChangesAndLocksSlugAfterFirstPublication() {
        Instant timestamp = now();
        Article article = newArticle(UUID.randomUUID(), "draft-slug", null, timestamp);

        article.changeSlug("  UPDATED-SLUG  ", slugPolicy, timestamp.plusSeconds(1));
        assertEquals(slugPolicy.create("updated-slug"), article.slug());

        Instant publishedAt = timestamp.plusSeconds(2);
        article.publish(publishedAt);
        article.changeSlug(" UPDATED-SLUG ", slugPolicy, timestamp.plusSeconds(3));

        SlugPolicy.PublishedSlugConflictException publishedConflict = assertThrows(
                SlugPolicy.PublishedSlugConflictException.class,
                () -> article.changeSlug("changed-after-publish", slugPolicy, timestamp.plusSeconds(4)));
        assertEquals(SlugPolicy.PUBLISHED_SLUG_IMMUTABLE_CODE, publishedConflict.code());

        article.archive(timestamp.plusSeconds(5));
        assertThrows(
                SlugPolicy.PublishedSlugConflictException.class,
                () -> article.changeSlug("changed-after-archive", slugPolicy, timestamp.plusSeconds(6)));

        inTransaction(() -> {
            articles.save(article);
            entityManager.flush();
        });
        Article reloaded = inTransaction(() -> articles.findById(article.id()).orElseThrow());
        assertEquals(ArticleStatus.ARCHIVED, reloaded.status());
        assertEquals(publishedAt, reloaded.publishedAt());
        assertEquals(slugPolicy.create("updated-slug"), reloaded.slug());
    }

    @Test
    void failedCommandsLeaveArticleAndBidirectionalRelationshipsUnchanged() {
        Instant timestamp = now();

        Article revision = newArticle(UUID.randomUUID(), "revision-atomic", null, timestamp);
        assertThrows(IllegalArgumentException.class, () -> revision.revise(
                "Changed title", " ", "Changed body", null, null, timestamp.plusSeconds(1)));
        assertEquals("Title revision-atomic", revision.title());
        assertEquals("Summary revision-atomic", revision.summary());
        assertEquals(timestamp, revision.updatedAt());

        Article slugChange = newArticle(UUID.randomUUID(), "slug-atomic", null, timestamp);
        assertThrows(
                NullPointerException.class,
                () -> slugChange.changeSlug("changed-slug", slugPolicy, null));
        assertEquals(slugPolicy.create("slug-atomic"), slugChange.slug());
        assertEquals(timestamp, slugChange.updatedAt());

        Article publication = newArticle(UUID.randomUUID(), "publish-atomic", null, timestamp);
        assertThrows(NullPointerException.class, () -> publication.publish(null));
        assertEquals(ArticleStatus.DRAFT, publication.status());
        assertNull(publication.publishedAt());
        assertEquals(timestamp, publication.updatedAt());

        Article archival = newArticle(UUID.randomUUID(), "archive-atomic", null, timestamp);
        archival.publish(timestamp.plusSeconds(1));
        assertThrows(NullPointerException.class, () -> archival.archive(null));
        assertEquals(ArticleStatus.PUBLISHED, archival.status());
        assertEquals(timestamp.plusSeconds(1), archival.updatedAt());

        Category oldCategory = newCategory("old-category", timestamp);
        Category newCategory = newCategory("new-category", timestamp);
        Article categoryChange = newArticle(
                UUID.randomUUID(), "category-atomic", oldCategory, timestamp);
        assertThrows(
                NullPointerException.class,
                () -> categoryChange.changeCategory(newCategory, null));
        assertSame(oldCategory, categoryChange.category());
        assertTrue(oldCategory.articles().contains(categoryChange));
        assertFalse(newCategory.articles().contains(categoryChange));
        assertEquals(timestamp, categoryChange.updatedAt());

        Tag addedTag = newTag("added-tag", timestamp);
        Article tagAddition = newArticle(UUID.randomUUID(), "tag-add-atomic", null, timestamp);
        assertThrows(NullPointerException.class, () -> tagAddition.addTag(addedTag, null));
        assertTrue(tagAddition.tags().isEmpty());
        assertFalse(addedTag.articles().contains(tagAddition));
        assertEquals(timestamp, tagAddition.updatedAt());

        Tag removedTag = newTag("removed-tag", timestamp);
        Article tagRemoval = newArticle(UUID.randomUUID(), "tag-remove-atomic", null, timestamp);
        tagRemoval.addTag(removedTag, timestamp.plusSeconds(1));
        assertThrows(NullPointerException.class, () -> tagRemoval.removeTag(removedTag, null));
        assertTrue(tagRemoval.tags().contains(removedTag));
        assertTrue(removedTag.articles().contains(tagRemoval));
        assertEquals(timestamp.plusSeconds(1), tagRemoval.updatedAt());
    }

    @Test
    void rejectsStaleArticleUpdatesWithOptimisticLock() {
        Instant timestamp = now();
        Article article = newArticle(UUID.randomUUID(), "optimistic-lock", null, timestamp);
        inTransaction(() -> {
            articles.save(article);
            entityManager.flush();
        });
        Article firstCopy = inTransaction(() -> articles.findById(article.id()).orElseThrow());
        Article staleCopy = inTransaction(() -> articles.findById(article.id()).orElseThrow());

        firstCopy.revise(
                "Updated title", "Updated summary", "Updated body", null, null, timestamp.plusSeconds(1));
        inTransaction(() -> {
            articles.save(firstCopy);
            entityManager.flush();
        });

        staleCopy.revise(
                "Stale title", "Stale summary", "Stale body", null, null, timestamp.plusSeconds(2));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> inTransaction(() -> {
            articles.save(staleCopy);
            entityManager.flush();
        }));
    }

    @Test
    void publicQueriesUseStableSortingFiltersPaginationAndVisibilityRules() {
        Instant currentTime = now();
        Instant tiedPublication = currentTime.minusSeconds(60);
        Category java = newCategory("java-category", currentTime);
        Category architecture = newCategory("architecture-category", currentTime);
        Tag spring = newTag("spring-tag", currentTime);
        Tag database = newTag("database-tag", currentTime);

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
                database);
        Article older = publishedArticle(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "older-article",
                architecture,
                currentTime.minusSeconds(120),
                spring);
        Article draft = newArticle(UUID.randomUUID(), "draft-hidden", java, currentTime);
        draft.addTag(spring, currentTime);
        Article future = publishedArticle(
                UUID.randomUUID(), "future-hidden", java, currentTime.plusSeconds(3600), spring);
        Article archived = publishedArticle(
                UUID.randomUUID(), "archived-hidden", java, currentTime.minusSeconds(30), spring);
        archived.archive(currentTime);

        inTransaction(() -> {
            categories.save(java);
            categories.save(architecture);
            tags.save(spring);
            tags.save(database);
            for (Article article : List.of(tiedLow, tiedHigh, older, draft, future, archived)) {
                articles.save(article);
            }
            entityManager.flush();
        });

        assertEquals(
                List.of("tied-high", "tied-low", "older-article"),
                publicSlugs(currentTime, null, null, 50, 0));
        assertEquals(
                List.of("tied-high", "tied-low"),
                publicSlugs(currentTime, "java-category", null, 50, 0));
        assertEquals(
                List.of("tied-low", "older-article"),
                publicSlugs(currentTime, null, "spring-tag", 50, 0));
        assertEquals(
                List.of("tied-low"),
                publicSlugs(currentTime, "java-category", "spring-tag", 50, 0));
        assertEquals(
                List.of("tied-low"),
                publicSlugs(currentTime, null, null, 1, 1));
        assertEquals(3, articles.countPublicArticles(currentTime, null, null));

        assertTrue(articles.findPublicBySlug("tied-high", currentTime).isPresent());
        assertTrue(articles.findPublicBySlug("draft-hidden", currentTime).isEmpty());
        assertTrue(articles.findPublicBySlug("future-hidden", currentTime).isEmpty());
        assertTrue(articles.findPublicBySlug("archived-hidden", currentTime).isEmpty());
        assertTrue(articles.findPublicBySlug("missing-article", currentTime).isEmpty());
    }

    @Test
    void taxonomyQueriesCountOnlyCurrentlyPublicArticlesInStableOrder() {
        Instant currentTime = now();
        Category java = newCategory("java-category", currentTime);
        Category architecture = newCategory("architecture-category", currentTime);
        Category emptyCategory = newCategory("empty-category", currentTime);
        Tag spring = newTag("spring-tag", currentTime);
        Tag database = newTag("database-tag", currentTime);
        Tag emptyTag = newTag("empty-tag", currentTime);

        Article javaArticle = publishedArticle(
                UUID.randomUUID(), "java-public", java, currentTime.minusSeconds(30), spring);
        Article architectureArticle = publishedArticle(
                UUID.randomUUID(), "architecture-public", architecture, currentTime.minusSeconds(60), spring);
        architectureArticle.addTag(database, currentTime.minusSeconds(61));
        Article draft = newArticle(UUID.randomUUID(), "draft-hidden", java, currentTime);
        draft.addTag(database, currentTime);
        Article future = publishedArticle(
                UUID.randomUUID(), "future-hidden", java, currentTime.plusSeconds(3600), database);
        Article archived = publishedArticle(
                UUID.randomUUID(), "archived-hidden", java, currentTime.minusSeconds(10), database);
        archived.archive(currentTime);

        inTransaction(() -> {
            for (Category category : List.of(java, architecture, emptyCategory)) {
                categories.save(category);
            }
            for (Tag tag : List.of(spring, database, emptyTag)) {
                tags.save(tag);
            }
            for (Article article : List.of(javaArticle, architectureArticle, draft, future, archived)) {
                articles.save(article);
            }
            entityManager.flush();
        });

        assertEquals(
                List.of(
                        "Category architecture-category|architecture-category:1",
                        "Category java-category|java-category:1"),
                categories.findPublicCategoryCounts(currentTime).stream()
                        .map(count -> count.name() + "|" + count.slug() + ":" + count.publishedArticleCount())
                        .toList());
        assertEquals(
                List.of(
                        "Tag database-tag|database-tag:1",
                        "Tag spring-tag|spring-tag:2"),
                tags.findPublicTagCounts(currentTime).stream()
                        .map(count -> count.name() + "|" + count.slug() + ":" + count.publishedArticleCount())
                        .toList());
    }

    @Test
    void repositoriesExposeNoArbitrarySortOrEntityPageContract() {
        for (Method method : ArticleRepository.class.getDeclaredMethods()) {
            assertFalse(Page.class.isAssignableFrom(method.getReturnType()));
            assertTrue(Arrays.stream(method.getParameterTypes())
                    .noneMatch(type -> type == Sort.class || type == Pageable.class));
        }
        assertTrue(Arrays.stream(Article.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")));
        assertTrue(Arrays.stream(Category.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")));
        assertTrue(Arrays.stream(Tag.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")));
    }

    private List<String> publicSlugs(
            Instant now,
            String categorySlug,
            String tagSlug,
            int limit,
            long offset) {
        return inTransaction(() -> articles.findPublicArticles(
                        now, categorySlug, tagSlug, limit, offset).stream()
                .map(article -> article.slug().value())
                .toList());
    }

    private Article publishedArticle(
            UUID id,
            String slug,
            Category category,
            Instant publishedAt,
            Tag tag) {
        Article article = newArticle(id, slug, category, publishedAt.minusSeconds(1));
        article.addTag(tag, publishedAt.minusSeconds(1));
        article.publish(publishedAt);
        return article;
    }

    private Article newArticle(UUID id, String slug, Category category, Instant timestamp) {
        return new Article(
                id,
                slugPolicy.create(slug),
                "Title " + slug,
                "Summary " + slug,
                "Body " + slug,
                category,
                null,
                null,
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

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
