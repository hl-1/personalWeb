package com.studystack.content.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.content.application.ArticleNotFoundException;
import com.studystack.content.application.PublicArticleQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
class ArticleAdminServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    ArticleAdminService articleAdmin;

    @Autowired
    PublicArticleQuery publicArticles;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "truncate table content_article_tag, content_article, content_category, content_tag cascade");
    }

    @Test
    void createsReadsAndFullyUpdatesDraftsWithTaxonomyAndVersions() {
        UUID firstCategory = insertCategory("first-category");
        UUID secondCategory = insertCategory("second-category");
        UUID firstTag = insertTag("first-tag");
        UUID secondTag = insertTag("second-tag");

        ArticleAdminView created = articleAdmin.create(createCommand(
                "managed-draft", firstCategory, List.of(firstTag, secondTag)));

        assertEquals(ArticleAdminView.Status.DRAFT, created.status());
        assertEquals(0L, created.version());
        assertEquals(firstCategory, created.categoryId());
        assertEquals(List.of(firstTag, secondTag).stream().sorted().toList(), created.tagIds());
        assertEquals(created, articleAdmin.find(created.id()));

        ArticleAdminView updated = articleAdmin.update(
                created.id(),
                new ArticleAdminCommand.Update(
                        "renamed-draft",
                        "Updated title",
                        "Updated summary",
                        "Updated body",
                        secondCategory,
                        List.of(secondTag),
                        "Updated SEO title",
                        "Updated SEO description",
                        created.version()));

        assertEquals("renamed-draft", updated.slug());
        assertEquals("Updated title", updated.title());
        assertEquals("Updated summary", updated.summary());
        assertEquals("Updated body", updated.bodyMarkdown());
        assertEquals(secondCategory, updated.categoryId());
        assertEquals(List.of(secondTag), updated.tagIds());
        assertEquals(1L, updated.version());
    }

    @Test
    void listsWithStablePaginationStatusAndCaseInsensitiveTitleOrSlugSearch() {
        ArticleAdminView alpha = articleAdmin.create(createCommand("alpha-slug", null, List.of()));
        ArticleAdminView beta = articleAdmin.create(new ArticleAdminCommand.Create(
                "beta-slug",
                "Searchable BETA title",
                "Summary",
                "Body",
                null,
                List.of(),
                null,
                null));
        ArticleAdminView gamma = articleAdmin.create(createCommand("gamma-slug", null, List.of()));
        articleAdmin.publish(beta.id(), beta.version(), null);
        ArticleAdminView publishedGamma = articleAdmin.publish(gamma.id(), gamma.version(), Instant.now());
        articleAdmin.archive(gamma.id(), publishedGamma.version());

        ArticleAdminPage firstPage = articleAdmin.list(0, 2, null, null);
        ArticleAdminPage secondPage = articleAdmin.list(1, 2, null, null);
        assertEquals(3, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(2, firstPage.content().size());
        assertEquals(1, secondPage.content().size());
        assertEquals(
                List.of(ArticleAdminView.Status.DRAFT),
                articleAdmin.list(0, 20, ArticleAdminView.Status.DRAFT, null).content().stream()
                        .map(ArticleAdminView.Summary::status)
                        .distinct()
                        .toList());
        assertEquals(
                List.of(beta.id()),
                articleAdmin.list(0, 20, null, " beta ").content().stream()
                        .map(ArticleAdminView.Summary::id)
                        .toList());
        assertEquals(
                List.of(alpha.id()),
                articleAdmin.list(0, 20, null, "ALPHA-SLUG").content().stream()
                        .map(ArticleAdminView.Summary::id)
                        .toList());
    }

    @Test
    void allowsDraftSlugChangesAndDeletionButLocksPublishedSlugsAndDeletion() {
        ArticleAdminView draft = articleAdmin.create(createCommand("draft-delete", null, List.of()));
        ArticleAdminView renamed = articleAdmin.update(
                draft.id(), updateCommand(draft, "renamed-before-delete", "Draft title"));
        articleAdmin.delete(renamed.id(), renamed.version());
        assertFailure(ArticleAdminService.Failure.NOT_FOUND, () -> articleAdmin.find(renamed.id()));

        ArticleAdminView published = articleAdmin.create(createCommand("published-lock", null, List.of()));
        published = articleAdmin.publish(published.id(), published.version(), null);
        ArticleAdminView publishedSnapshot = published;
        assertFailure(
                ArticleAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> articleAdmin.update(
                        publishedSnapshot.id(),
                        updateCommand(publishedSnapshot, "changed-after-publish", "Changed title")));

        ArticleAdminView revised = articleAdmin.update(
                published.id(), updateCommand(published, published.slug(), "Published revision"));
        assertEquals("Published revision", revised.title());
        assertFailure(
                ArticleAdminService.Failure.DRAFT_DELETE_ONLY,
                () -> articleAdmin.delete(revised.id(), revised.version()));
    }

    @Test
    void enforcesTheForwardOnlyStateMachineAndArchivedReadOnlyContract() {
        ArticleAdminView draft = articleAdmin.create(createCommand("state-machine", null, List.of()));
        assertFailure(
                ArticleAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> articleAdmin.archive(draft.id(), draft.version()));

        ArticleAdminView published = articleAdmin.publish(draft.id(), draft.version(), null);
        assertFailure(
                ArticleAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> articleAdmin.publish(published.id(), published.version(), null));

        ArticleAdminView archived = articleAdmin.archive(published.id(), published.version());
        assertEquals(ArticleAdminView.Status.ARCHIVED, archived.status());
        assertFailure(
                ArticleAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> articleAdmin.update(
                        archived.id(), updateCommand(archived, archived.slug(), "Archived revision")));
        assertFailure(
                ArticleAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> articleAdmin.publish(archived.id(), archived.version(), null));
        assertFailure(
                ArticleAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> articleAdmin.archive(archived.id(), archived.version()));
        assertFailure(
                ArticleAdminService.Failure.DRAFT_DELETE_ONLY,
                () -> articleAdmin.delete(archived.id(), archived.version()));
    }

    @Test
    void supportsImmediateAndFuturePublicationWithoutChangingPublicVisibilityRules() {
        ArticleAdminView immediate = articleAdmin.create(createCommand("immediate-public", null, List.of()));
        Instant beforePublish = Instant.now();
        immediate = articleAdmin.publish(immediate.id(), immediate.version(), null);
        assertNotNull(immediate.publishedAt());
        assertTrue(!immediate.publishedAt().isBefore(beforePublish));
        assertEquals("immediate-public", publicArticles.findArticle("immediate-public").slug());

        ArticleAdminView future = articleAdmin.create(createCommand("future-public", null, List.of()));
        Instant futureTime = Instant.now()
                .plus(1, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.MICROS);
        future = articleAdmin.publish(future.id(), future.version(), futureTime);
        assertEquals(futureTime, future.publishedAt());
        assertTrue(future.updatedAt().isBefore(future.publishedAt()));
        assertThrows(ArticleNotFoundException.class, () -> publicArticles.findArticle("future-public"));
    }

    @Test
    void validatesTaxonomyCompletelyBeforeReplacingRelationships() {
        UUID category = insertCategory("valid-category");
        UUID tag = insertTag("valid-tag");
        ArticleAdminView article = articleAdmin.create(createCommand(
                "taxonomy-atomic", category, List.of(tag)));

        assertFailure(
                ArticleAdminService.Failure.NOT_FOUND,
                () -> articleAdmin.create(createCommand(
                        "unknown-category", UUID.randomUUID(), List.of(tag))));
        assertFailure(
                ArticleAdminService.Failure.NOT_FOUND,
                () -> articleAdmin.update(
                        article.id(),
                        new ArticleAdminCommand.Update(
                                article.slug(),
                                "Changed",
                                article.summary(),
                                article.bodyMarkdown(),
                                category,
                                List.of(tag, UUID.randomUUID()),
                                article.seoTitle(),
                                article.seoDescription(),
                                article.version())));
        assertThrows(
                IllegalArgumentException.class,
                () -> articleAdmin.create(createCommand(
                        "duplicate-tags", category, List.of(tag, tag))));

        List<UUID> elevenTags = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            elevenTags.add(insertTag("limit-tag-" + index));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> articleAdmin.create(createCommand("too-many-tags", category, elevenTags)));

        ArticleAdminView unchanged = articleAdmin.find(article.id());
        assertEquals(category, unchanged.categoryId());
        assertEquals(List.of(tag), unchanged.tagIds());
        assertEquals("Title taxonomy-atomic", unchanged.title());
    }

    @Test
    void mapsDuplicateSlugAndStaleVersionToStableFailures() {
        ArticleAdminView first = articleAdmin.create(createCommand("duplicate-slug", null, List.of()));
        assertFailure(
                ArticleAdminService.Failure.DUPLICATE_SLUG,
                () -> articleAdmin.create(createCommand(" DUPLICATE-SLUG ", null, List.of())));

        ArticleAdminView updated = articleAdmin.update(
                first.id(), updateCommand(first, first.slug(), "First update"));
        assertFailure(
                ArticleAdminService.Failure.STALE_VERSION,
                () -> articleAdmin.update(
                        first.id(), updateCommand(first, first.slug(), "Stale update")));
        assertEquals("First update", articleAdmin.find(updated.id()).title());
    }

    @Test
    void permitsOnlyOneOfTwoConcurrentUpdatesFromTheSameVersion() throws Exception {
        ArticleAdminView article = articleAdmin.create(createCommand("concurrent-update", null, List.of()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> updateAfterSignal(
                    start, article, "Concurrent first"));
            Future<Object> second = executor.submit(() -> updateAfterSignal(
                    start, article, "Concurrent second"));
            start.countDown();

            List<Object> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(ArticleAdminView.class::isInstance).count());
            Object failure = results.stream()
                    .filter(ArticleAdminService.ArticleAdminException.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            ArticleAdminService.ArticleAdminException conflict = assertInstanceOf(
                    ArticleAdminService.ArticleAdminException.class, failure);
            assertEquals(ArticleAdminService.Failure.STALE_VERSION, conflict.failure());
            assertEquals(1L, articleAdmin.find(article.id()).version());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsDomainRepositoriesOutOfTheNamedInterfacePublicApi() {
        assertEquals(0, ArticleAdminService.class.getConstructors().length);
        assertTrue(Arrays.stream(ArticleAdminService.class.getMethods())
                .filter(method -> method.getDeclaringClass() == ArticleAdminService.class)
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(type -> type.getPackageName().equals("com.studystack.content.domain")));
    }

    private Object updateAfterSignal(
            CountDownLatch start,
            ArticleAdminView article,
            String title) throws InterruptedException {
        start.await();
        try {
            return articleAdmin.update(
                    article.id(), updateCommand(article, article.slug(), title));
        } catch (ArticleAdminService.ArticleAdminException exception) {
            return exception;
        }
    }

    private ArticleAdminCommand.Create createCommand(
            String slug,
            UUID categoryId,
            List<UUID> tagIds) {
        return new ArticleAdminCommand.Create(
                slug,
                "Title " + slug,
                "Summary " + slug,
                "Body " + slug,
                categoryId,
                tagIds,
                null,
                null);
    }

    private ArticleAdminCommand.Update updateCommand(
            ArticleAdminView article,
            String slug,
            String title) {
        return new ArticleAdminCommand.Update(
                slug,
                title,
                article.summary(),
                article.bodyMarkdown(),
                article.categoryId(),
                article.tagIds(),
                article.seoTitle(),
                article.seoDescription(),
                article.version());
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
                "Category " + slug,
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
                "Tag " + slug,
                slug,
                now,
                now);
        return id;
    }

    private void assertFailure(ArticleAdminService.Failure expected, Runnable action) {
        ArticleAdminService.ArticleAdminException failure = assertThrows(
                ArticleAdminService.ArticleAdminException.class,
                action::run);
        assertEquals(expected, failure.failure());
    }
}
