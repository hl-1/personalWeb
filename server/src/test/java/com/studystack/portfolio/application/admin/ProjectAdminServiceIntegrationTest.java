package com.studystack.portfolio.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.portfolio.application.PortfolioNotFoundException;
import com.studystack.portfolio.application.PublicPortfolioQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ProjectAdminServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    ProjectAdminService projectAdmin;

    @Autowired
    PublicPortfolioQuery publicPortfolio;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table portfolio_project");
    }

    @Test
    void createsFindsAndFullyUpdatesDraftsWithNormalizedUrlsAndVersions() {
        ProjectAdminView created = projectAdmin.create(new ProjectAdminCommand.Create(
                "managed-project",
                "Managed project",
                "Managed summary",
                "# Managed description",
                " HTTPS://Example.COM/work/../project ",
                "https://GITHUB.COM/example/project",
                true,
                7));

        assertEquals(ProjectAdminView.Status.DRAFT, created.status());
        assertEquals("https://example.com/project", created.projectUrl());
        assertEquals("https://github.com/example/project", created.repositoryUrl());
        assertEquals(7, created.sortOrder());
        assertEquals(0L, created.version());
        assertEquals(created, projectAdmin.find(created.id()));

        ProjectAdminView updated = projectAdmin.update(
                created.id(),
                new ProjectAdminCommand.Update(
                        "renamed-project",
                        "Updated project",
                        "Updated summary",
                        "Updated description",
                        null,
                        null,
                        false,
                        0,
                        created.version()));

        assertEquals("renamed-project", updated.slug());
        assertEquals("Updated project", updated.title());
        assertEquals("Updated summary", updated.summary());
        assertEquals("Updated description", updated.descriptionMarkdown());
        assertNull(updated.projectUrl());
        assertNull(updated.repositoryUrl());
        assertEquals(1L, updated.version());
    }

    @Test
    void listsWithStablePaginationStatusAndCaseInsensitiveTitleOrSlugSearch() {
        Instant timestamp = now().minusSeconds(60);
        insertProject(id(1), "alpha-project", "Alpha title", "DRAFT", timestamp, null);
        insertProject(id(2), "beta-project", "Searchable BETA title", "PUBLISHED", timestamp, timestamp);
        insertProject(id(3), "gamma-project", "Gamma title", "ARCHIVED", timestamp, timestamp);

        ProjectAdminPage firstPage = projectAdmin.list(0, 2, null, null);
        ProjectAdminPage secondPage = projectAdmin.list(1, 2, null, null);
        assertEquals(3, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(List.of(id(3), id(2)), firstPage.content().stream()
                .map(ProjectAdminView.Summary::id)
                .toList());
        assertEquals(List.of(id(1)), secondPage.content().stream()
                .map(ProjectAdminView.Summary::id)
                .toList());
        assertEquals(
                List.of(ProjectAdminView.Status.DRAFT),
                projectAdmin.list(0, 20, ProjectAdminView.Status.DRAFT, null).content().stream()
                        .map(ProjectAdminView.Summary::status)
                        .distinct()
                        .toList());
        assertEquals(
                List.of(id(2)),
                projectAdmin.list(0, 20, null, " beta ").content().stream()
                        .map(ProjectAdminView.Summary::id)
                        .toList());
        assertEquals(
                List.of(id(1)),
                projectAdmin.list(0, 20, null, "ALPHA-PROJECT").content().stream()
                        .map(ProjectAdminView.Summary::id)
                        .toList());

        assertThrows(IllegalArgumentException.class, () -> projectAdmin.list(-1, 20, null, null));
        assertThrows(IllegalArgumentException.class, () -> projectAdmin.list(0, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> projectAdmin.list(0, 101, null, null));
        assertThrows(IllegalArgumentException.class, () -> projectAdmin.list(0, 20, null, "x".repeat(101)));
    }

    @Test
    void supportsImmediateAndFuturePublicationWithoutChangingPublicVisibilityRules() {
        ProjectAdminView immediate = projectAdmin.create(createCommand("immediate-project"));
        Instant beforePublish = Instant.now();
        immediate = projectAdmin.publish(immediate.id(), immediate.version(), null);
        assertNotNull(immediate.publishedAt());
        assertTrue(!immediate.publishedAt().isBefore(beforePublish));
        assertEquals("immediate-project", publicPortfolio.findProject("immediate-project").slug());

        ProjectAdminView future = projectAdmin.create(createCommand("future-project"));
        Instant futureTime = now().plus(1, ChronoUnit.DAYS);
        future = projectAdmin.publish(future.id(), future.version(), futureTime);
        assertEquals(futureTime, future.publishedAt());
        assertTrue(future.updatedAt().isBefore(future.publishedAt()));
        assertThrows(PortfolioNotFoundException.class, () -> publicPortfolio.findProject("future-project"));
    }

    @Test
    void enforcesDraftDeletionSlugLockAndForwardOnlyStateTransitions() {
        ProjectAdminView draft = projectAdmin.create(createCommand("draft-project"));
        ProjectAdminView renamed = projectAdmin.update(
                draft.id(), updateCommand(draft, "renamed-draft", "Renamed draft"));
        projectAdmin.delete(renamed.id(), renamed.version());
        assertFailure(ProjectAdminService.Failure.NOT_FOUND, () -> projectAdmin.find(renamed.id()));

        ProjectAdminView published = projectAdmin.create(createCommand("published-project"));
        published = projectAdmin.publish(published.id(), published.version(), null);
        ProjectAdminView publishedSnapshot = published;
        assertFailure(
                ProjectAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> projectAdmin.update(
                        publishedSnapshot.id(),
                        updateCommand(publishedSnapshot, "changed-after-publish", "Changed")));
        ProjectAdminView revised = projectAdmin.update(
                published.id(), updateCommand(published, published.slug(), "Published revision"));
        assertEquals("Published revision", revised.title());
        assertFailure(
                ProjectAdminService.Failure.DRAFT_DELETE_ONLY,
                () -> projectAdmin.delete(revised.id(), revised.version()));
        assertFailure(
                ProjectAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> projectAdmin.publish(revised.id(), revised.version(), null));

        ProjectAdminView archived = projectAdmin.archive(revised.id(), revised.version());
        assertEquals(ProjectAdminView.Status.ARCHIVED, archived.status());
        assertFailure(
                ProjectAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> projectAdmin.update(
                        archived.id(), updateCommand(archived, archived.slug(), "Archived revision")));
        assertFailure(
                ProjectAdminService.Failure.INVALID_STATE_TRANSITION,
                () -> projectAdmin.archive(archived.id(), archived.version()));
        assertFailure(
                ProjectAdminService.Failure.DRAFT_DELETE_ONLY,
                () -> projectAdmin.delete(archived.id(), archived.version()));
    }

    @Test
    void rejectsInvalidUrlsSortOrdersAndLeavesExistingStateUnchanged() {
        for (String url : List.of(
                "http://example.com/project",
                "/relative-project",
                "https://user:secret@example.com/project",
                "https://example.com/has space",
                " ")) {
            assertThrows(IllegalArgumentException.class, () -> projectAdmin.create(
                    commandWith("invalid-url", url, null, 0)));
            assertThrows(IllegalArgumentException.class, () -> projectAdmin.create(
                    commandWith("invalid-repository", null, url, 0)));
        }
        assertThrows(IllegalArgumentException.class, () -> projectAdmin.create(
                commandWith("negative-order", null, null, -1)));

        ProjectAdminView existing = projectAdmin.create(createCommand("unchanged-project"));
        assertThrows(IllegalArgumentException.class, () -> projectAdmin.update(
                existing.id(),
                new ProjectAdminCommand.Update(
                        existing.slug(),
                        "Changed title",
                        existing.summary(),
                        existing.descriptionMarkdown(),
                        "http://unsafe.example",
                        existing.repositoryUrl(),
                        existing.featured(),
                        existing.sortOrder(),
                        existing.version())));
        assertEquals(existing, projectAdmin.find(existing.id()));
    }

    @Test
    void mapsDuplicateSlugAndStaleVersionToStableFailures() {
        ProjectAdminView first = projectAdmin.create(createCommand("duplicate-project"));
        assertFailure(
                ProjectAdminService.Failure.DUPLICATE_SLUG,
                () -> projectAdmin.create(createCommand(" DUPLICATE-PROJECT ")));

        ProjectAdminView updated = projectAdmin.update(
                first.id(), updateCommand(first, first.slug(), "First update"));
        assertFailure(
                ProjectAdminService.Failure.STALE_VERSION,
                () -> projectAdmin.update(
                        first.id(), updateCommand(first, first.slug(), "Stale update")));
        assertEquals("First update", projectAdmin.find(updated.id()).title());
    }

    @Test
    void permitsOnlyOneOfTwoConcurrentUpdatesFromTheSameVersion() throws Exception {
        ProjectAdminView project = projectAdmin.create(createCommand("concurrent-project"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> updateAfterSignal(start, project, "First"));
            Future<Object> second = executor.submit(() -> updateAfterSignal(start, project, "Second"));
            start.countDown();

            List<Object> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(ProjectAdminView.class::isInstance).count());
            ProjectAdminService.ProjectAdminException failure = assertInstanceOf(
                    ProjectAdminService.ProjectAdminException.class,
                    results.stream()
                            .filter(ProjectAdminService.ProjectAdminException.class::isInstance)
                            .findFirst()
                            .orElseThrow());
            assertEquals(ProjectAdminService.Failure.STALE_VERSION, failure.failure());
            assertEquals(1L, projectAdmin.find(project.id()).version());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsDomainRepositoriesOutOfTheNamedInterfacePublicApi() {
        assertEquals(0, ProjectAdminService.class.getConstructors().length);
        assertTrue(Arrays.stream(ProjectAdminService.class.getMethods())
                .filter(method -> method.getDeclaringClass() == ProjectAdminService.class)
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(type -> type.getPackageName().equals("com.studystack.portfolio.domain")));
    }

    private ProjectAdminCommand.Create createCommand(String slug) {
        return commandWith(slug, null, null, 0);
    }

    private ProjectAdminCommand.Create commandWith(
            String slug,
            String projectUrl,
            String repositoryUrl,
            int sortOrder) {
        return new ProjectAdminCommand.Create(
                slug,
                "Title " + slug,
                "Summary " + slug,
                "Description " + slug,
                projectUrl,
                repositoryUrl,
                false,
                sortOrder);
    }

    private ProjectAdminCommand.Update updateCommand(
            ProjectAdminView project,
            String slug,
            String title) {
        return new ProjectAdminCommand.Update(
                slug,
                title,
                project.summary(),
                project.descriptionMarkdown(),
                project.projectUrl(),
                project.repositoryUrl(),
                project.featured(),
                project.sortOrder(),
                project.version());
    }

    private Object updateAfterSignal(
            CountDownLatch start,
            ProjectAdminView project,
            String suffix) throws InterruptedException {
        start.await();
        try {
            return projectAdmin.update(
                    project.id(), updateCommand(project, project.slug(), "Concurrent " + suffix));
        } catch (ProjectAdminService.ProjectAdminException exception) {
            return exception;
        }
    }

    private void insertProject(
            UUID id,
            String slug,
            String title,
            String status,
            Instant updatedAt,
            Instant publishedAt) {
        Timestamp timestamp = Timestamp.from(updatedAt);
        jdbcTemplate.update("""
                insert into portfolio_project (
                    id, slug, title, summary, description_markdown, status, featured,
                    sort_order, published_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, false, 0, ?, ?, ?)
                """,
                id,
                slug,
                title,
                "Summary " + slug,
                "Description " + slug,
                status,
                publishedAt == null ? null : Timestamp.from(publishedAt),
                timestamp,
                timestamp);
    }

    private UUID id(long value) {
        return new UUID(0, value);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void assertFailure(ProjectAdminService.Failure expected, Runnable action) {
        ProjectAdminService.ProjectAdminException failure = assertThrows(
                ProjectAdminService.ProjectAdminException.class,
                action::run);
        assertEquals(expected, failure.failure());
    }
}
