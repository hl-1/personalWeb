package com.studystack.content.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
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
class TaxonomyAdminServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    TaxonomyAdminService taxonomyAdmin;

    @Autowired
    ArticleAdminService articleAdmin;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "truncate table content_article_tag, content_article, content_category, content_tag cascade");
    }

    @Test
    void createsListsUpdatesAndDeletesCategoriesAndTagsWithStableOrdering() {
        TaxonomyAdminView zetaCategory = taxonomyAdmin.createCategory(
                new TaxonomyAdminCommand.Create("Zeta category", "zeta-category"));
        TaxonomyAdminView alphaCategory = taxonomyAdmin.createCategory(
                new TaxonomyAdminCommand.Create("Alpha category", "alpha-category"));
        TaxonomyAdminView zetaTag = taxonomyAdmin.createTag(
                new TaxonomyAdminCommand.Create("Zeta tag", "zeta-tag"));
        TaxonomyAdminView alphaTag = taxonomyAdmin.createTag(
                new TaxonomyAdminCommand.Create("Alpha tag", "alpha-tag"));

        assertEquals(0L, alphaCategory.version());
        assertEquals(0L, alphaCategory.articleCount());
        assertEquals(
                List.of(alphaCategory.id(), zetaCategory.id()),
                taxonomyAdmin.listCategories().stream().map(TaxonomyAdminView::id).toList());
        assertEquals(
                List.of(alphaTag.id(), zetaTag.id()),
                taxonomyAdmin.listTags().stream().map(TaxonomyAdminView::id).toList());

        TaxonomyAdminView updatedCategory = taxonomyAdmin.updateCategory(
                alphaCategory.id(),
                new TaxonomyAdminCommand.Update(
                        "Updated category", "updated-category", alphaCategory.version()));
        TaxonomyAdminView updatedTag = taxonomyAdmin.updateTag(
                alphaTag.id(),
                new TaxonomyAdminCommand.Update("Updated tag", "updated-tag", alphaTag.version()));

        assertEquals("Updated category", updatedCategory.name());
        assertEquals("updated-category", updatedCategory.slug());
        assertEquals(1L, updatedCategory.version());
        assertEquals("Updated tag", updatedTag.name());
        assertEquals(1L, updatedTag.version());

        taxonomyAdmin.deleteCategory(zetaCategory.id(), zetaCategory.version());
        taxonomyAdmin.deleteTag(zetaTag.id(), zetaTag.version());
        assertEquals(List.of(updatedCategory.id()), taxonomyAdmin.listCategories().stream()
                .map(TaxonomyAdminView::id)
                .toList());
        assertEquals(List.of(updatedTag.id()), taxonomyAdmin.listTags().stream()
                .map(TaxonomyAdminView::id)
                .toList());
    }

    @Test
    void countsReferencesAndRejectsDeletionForDraftPublishedAndArchivedArticles() {
        TaxonomyAdminView category = taxonomyAdmin.createCategory(
                new TaxonomyAdminCommand.Create("Used category", "used-category"));
        TaxonomyAdminView tag = taxonomyAdmin.createTag(
                new TaxonomyAdminCommand.Create("Used tag", "used-tag"));

        ArticleAdminView draft = createArticle("taxonomy-draft", category.id(), tag.id());
        ArticleAdminView published = createArticle("taxonomy-published", category.id(), tag.id());
        articleAdmin.publish(published.id(), published.version(), null);
        ArticleAdminView archived = createArticle("taxonomy-archived", category.id(), tag.id());
        archived = articleAdmin.publish(archived.id(), archived.version(), null);
        articleAdmin.archive(archived.id(), archived.version());

        assertEquals(3L, taxonomyAdmin.listCategories().getFirst().articleCount());
        assertEquals(3L, taxonomyAdmin.listTags().getFirst().articleCount());
        assertFailure(
                TaxonomyAdminService.Failure.TAXONOMY_IN_USE,
                () -> taxonomyAdmin.deleteCategory(category.id(), category.version()));
        assertFailure(
                TaxonomyAdminService.Failure.TAXONOMY_IN_USE,
                () -> taxonomyAdmin.deleteTag(tag.id(), tag.version()));

        ArticleAdminView unchangedDraft = articleAdmin.find(draft.id());
        assertEquals(category.id(), unchangedDraft.categoryId());
        assertEquals(List.of(tag.id()), unchangedDraft.tagIds());
        assertEquals(3L, jdbcTemplate.queryForObject(
                "select count(*) from content_article where category_id = ?",
                Long.class,
                category.id()));
        assertEquals(3L, jdbcTemplate.queryForObject(
                "select count(*) from content_article_tag where tag_id = ?",
                Long.class,
                tag.id()));
    }

    @Test
    void mapsDuplicateNamesSlugsAndStaleVersionsToTypedFailures() {
        TaxonomyAdminView category = taxonomyAdmin.createCategory(
                new TaxonomyAdminCommand.Create("Unique category", "unique-category"));
        TaxonomyAdminView tag = taxonomyAdmin.createTag(
                new TaxonomyAdminCommand.Create("Unique tag", "unique-tag"));

        assertFailure(
                TaxonomyAdminService.Failure.DUPLICATE_SLUG,
                () -> taxonomyAdmin.createCategory(
                        new TaxonomyAdminCommand.Create("Unique category", "another-category")));
        assertFailure(
                TaxonomyAdminService.Failure.DUPLICATE_SLUG,
                () -> taxonomyAdmin.createCategory(
                        new TaxonomyAdminCommand.Create("Another category", " UNIQUE-CATEGORY ")));
        assertFailure(
                TaxonomyAdminService.Failure.DUPLICATE_SLUG,
                () -> taxonomyAdmin.createTag(
                        new TaxonomyAdminCommand.Create("Unique tag", "another-tag")));
        assertFailure(
                TaxonomyAdminService.Failure.DUPLICATE_SLUG,
                () -> taxonomyAdmin.createTag(
                        new TaxonomyAdminCommand.Create("Another tag", " UNIQUE-TAG ")));

        assertThrows(
                IllegalArgumentException.class,
                () -> taxonomyAdmin.createCategory(
                        new TaxonomyAdminCommand.Create("Invalid", "--invalid--")));
        assertThrows(
                IllegalArgumentException.class,
                () -> taxonomyAdmin.createTag(
                        new TaxonomyAdminCommand.Create("Invalid", "x")));

        taxonomyAdmin.updateCategory(
                category.id(),
                new TaxonomyAdminCommand.Update(
                        "Updated category", category.slug(), category.version()));
        taxonomyAdmin.updateTag(
                tag.id(),
                new TaxonomyAdminCommand.Update("Updated tag", tag.slug(), tag.version()));
        assertFailure(
                TaxonomyAdminService.Failure.STALE_VERSION,
                () -> taxonomyAdmin.updateCategory(
                        category.id(),
                        new TaxonomyAdminCommand.Update("Stale", "stale-category", category.version())));
        assertFailure(
                TaxonomyAdminService.Failure.STALE_VERSION,
                () -> taxonomyAdmin.deleteTag(tag.id(), tag.version()));
        assertFailure(
                TaxonomyAdminService.Failure.NOT_FOUND,
                () -> taxonomyAdmin.deleteCategory(UUID.randomUUID(), 0));
    }

    @Test
    void databaseEnforcesCategoryAndTagNameUniqueness() {
        taxonomyAdmin.createCategory(new TaxonomyAdminCommand.Create("Database category", "database-category"));
        taxonomyAdmin.createTag(new TaxonomyAdminCommand.Create("Database tag", "database-tag"));
        Timestamp now = Timestamp.from(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                insert into content_category (id, name, slug, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "Database category",
                "different-category",
                now,
                now));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                insert into content_tag (id, name, slug, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "Database tag",
                "different-tag",
                now,
                now));
    }

    @Test
    void permitsOnlyOneOfTwoConcurrentCategoryUpdatesFromTheSameVersion() throws Exception {
        TaxonomyAdminView category = taxonomyAdmin.createCategory(
                new TaxonomyAdminCommand.Create("Concurrent category", "concurrent-category"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> updateAfterSignal(start, category, "First"));
            Future<Object> second = executor.submit(() -> updateAfterSignal(start, category, "Second"));
            start.countDown();

            List<Object> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(TaxonomyAdminView.class::isInstance).count());
            TaxonomyAdminService.TaxonomyAdminException failure = assertInstanceOf(
                    TaxonomyAdminService.TaxonomyAdminException.class,
                    results.stream()
                            .filter(TaxonomyAdminService.TaxonomyAdminException.class::isInstance)
                            .findFirst()
                            .orElseThrow());
            assertEquals(TaxonomyAdminService.Failure.STALE_VERSION, failure.failure());
            assertEquals(1L, taxonomyAdmin.listCategories().getFirst().version());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsDomainRepositoriesOutOfTheNamedInterfacePublicApi() {
        assertEquals(0, TaxonomyAdminService.class.getConstructors().length);
        assertTrue(Arrays.stream(TaxonomyAdminService.class.getMethods())
                .filter(method -> method.getDeclaringClass() == TaxonomyAdminService.class)
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(type -> type.getPackageName().equals("com.studystack.content.domain")));
    }

    private ArticleAdminView createArticle(String slug, UUID categoryId, UUID tagId) {
        return articleAdmin.create(new ArticleAdminCommand.Create(
                slug,
                "Title " + slug,
                "Summary " + slug,
                "Body " + slug,
                categoryId,
                List.of(tagId),
                null,
                null));
    }

    private Object updateAfterSignal(
            CountDownLatch start,
            TaxonomyAdminView category,
            String suffix) throws InterruptedException {
        start.await();
        try {
            return taxonomyAdmin.updateCategory(
                    category.id(),
                    new TaxonomyAdminCommand.Update(
                            "Concurrent " + suffix,
                            "concurrent-" + suffix.toLowerCase(),
                            category.version()));
        } catch (TaxonomyAdminService.TaxonomyAdminException exception) {
            return exception;
        }
    }

    private void assertFailure(TaxonomyAdminService.Failure expected, Runnable action) {
        TaxonomyAdminService.TaxonomyAdminException failure = assertThrows(
                TaxonomyAdminService.TaxonomyAdminException.class,
                action::run);
        assertEquals(expected, failure.failure());
    }
}
