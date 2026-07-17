package com.studystack.portfolio.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.portfolio.domain.Experience;
import com.studystack.portfolio.domain.ExperienceRepository;
import com.studystack.portfolio.domain.PortfolioProfile;
import com.studystack.portfolio.domain.PortfolioProfileRepository;
import com.studystack.portfolio.domain.Project;
import com.studystack.portfolio.domain.ProjectRepository;
import com.studystack.portfolio.domain.ProjectStatus;
import com.studystack.portfolio.domain.Skill;
import com.studystack.portfolio.domain.SkillRepository;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
class PortfolioRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    PortfolioProfileRepository profiles;

    @Autowired
    ProjectRepository projects;

    @Autowired
    SkillRepository skills;

    @Autowired
    ExperienceRepository experiences;

    @Autowired
    SlugPolicy slugPolicy;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPortfolioData() {
        jdbcTemplate.execute(
                "truncate table portfolio_project, portfolio_skill, portfolio_experience, portfolio_profile");
    }

    @Test
    void persistsOnlyTheFixedSingletonProfileWithExactV4Mapping() {
        Instant timestamp = now();
        PortfolioProfile profile = new PortfolioProfile(
                "StudyStack Author",
                "Java and platform engineering",
                "# Biography",
                "Public profile description",
                timestamp);

        assertEquals(PortfolioProfile.SINGLETON_ID, profile.id());
        assertNull(profile.version());
        inTransaction(() -> {
            profiles.save(profile);
            entityManager.flush();
        });

        PortfolioProfile reloaded = profiles.findById(PortfolioProfile.SINGLETON_ID).orElseThrow();
        assertEquals("StudyStack Author", reloaded.displayName());
        assertEquals("Java and platform engineering", reloaded.headline());
        assertEquals("# Biography", reloaded.bioMarkdown());
        assertEquals("Public profile description", reloaded.seoDescription());
        assertEquals(timestamp, reloaded.createdAt());
        assertEquals(timestamp, reloaded.updatedAt());
        assertEquals(0L, reloaded.version());

    }

    @Test
    void persistsExactProjectMappingAndNormalizesHttpsUrlsOnce() {
        Instant timestamp = now();
        Project project = new Project(
                UUID.randomUUID(),
                slugPolicy.create("portfolio-project"),
                "Portfolio project",
                "Project summary",
                "# Project description",
                "  HTTPS://Example.COM/work/../project  ",
                "https://GITHUB.COM/example/project",
                true,
                7,
                timestamp);

        assertEquals("https://example.com/project", project.projectUrl());
        assertEquals("https://github.com/example/project", project.repositoryUrl());
        assertEquals(ProjectStatus.DRAFT, project.status());
        assertNull(project.publishedAt());
        assertNull(project.version());

        inTransaction(() -> {
            projects.save(project);
            entityManager.flush();
        });
        Project reloaded = projects.findById(project.id()).orElseThrow();
        assertEquals(slugPolicy.create("portfolio-project"), reloaded.slug());
        assertEquals("Portfolio project", reloaded.title());
        assertEquals("Project summary", reloaded.summary());
        assertEquals("# Project description", reloaded.descriptionMarkdown());
        assertEquals("https://example.com/project", reloaded.projectUrl());
        assertEquals("https://github.com/example/project", reloaded.repositoryUrl());
        assertTrue(reloaded.featured());
        assertEquals(7, reloaded.sortOrder());
        assertEquals(timestamp, reloaded.createdAt());
        assertEquals(timestamp, reloaded.updatedAt());
        assertEquals(0L, reloaded.version());
        assertTrue(projects.findBySlug(project.slug()).isPresent());
    }

    @Test
    void rejectsUnsafeMalformedAndOversizedProjectUrls() {
        for (String url : List.of(
                "http://example.com/project",
                "/relative-project",
                "https://",
                "https://user:secret@example.com/project",
                "https://example.com/has space",
                "https://example.com/" + "a".repeat(2_100))) {
            assertThrows(IllegalArgumentException.class, () -> newProject(
                    UUID.randomUUID(), "invalid-url", url, null, false, 0, now()));
            assertThrows(IllegalArgumentException.class, () -> newProject(
                    UUID.randomUUID(), "invalid-repository", null, url, false, 0, now()));
        }
    }

    @Test
    void supportsProjectLifecyclePresentationAndPermanentSlugLocking() {
        Instant timestamp = now();
        Project project = newProject(
                UUID.randomUUID(), "draft-project", null, null, false, 0, timestamp);

        project.changeSlug("  UPDATED-PROJECT  ", slugPolicy, timestamp.plusSeconds(1));
        project.changePresentation(true, 4, timestamp.plusSeconds(2));
        assertEquals(slugPolicy.create("updated-project"), project.slug());
        assertTrue(project.featured());
        assertEquals(4, project.sortOrder());

        Instant publishedAt = timestamp.plusSeconds(3);
        project.publish(publishedAt);
        assertEquals(ProjectStatus.PUBLISHED, project.status());
        assertEquals(publishedAt, project.publishedAt());
        project.changeSlug(" UPDATED-PROJECT ", slugPolicy, timestamp.plusSeconds(4));

        SlugPolicy.PublishedSlugConflictException conflict = assertThrows(
                SlugPolicy.PublishedSlugConflictException.class,
                () -> project.changeSlug("changed-project", slugPolicy, timestamp.plusSeconds(5)));
        assertEquals(SlugPolicy.PUBLISHED_SLUG_IMMUTABLE_CODE, conflict.code());

        project.archive(timestamp.plusSeconds(6));
        assertEquals(ProjectStatus.ARCHIVED, project.status());
        assertEquals(publishedAt, project.publishedAt());
        assertThrows(
                SlugPolicy.PublishedSlugConflictException.class,
                () -> project.changeSlug("changed-archived", slugPolicy, timestamp.plusSeconds(7)));
        assertThrows(IllegalStateException.class, () -> project.publish(timestamp.plusSeconds(8)));
    }

    @Test
    void failedProjectCommandsLeaveStateUnchanged() {
        Instant timestamp = now();
        Project project = newProject(
                UUID.randomUUID(), "atomic-project", null, null, false, 2, timestamp);

        assertThrows(
                IllegalArgumentException.class,
                () -> project.changePresentation(true, -1, timestamp.plusSeconds(1)));
        assertFalse(project.featured());
        assertEquals(2, project.sortOrder());
        assertEquals(timestamp, project.updatedAt());

        assertThrows(
                NullPointerException.class,
                () -> project.changeSlug("changed-project", slugPolicy, null));
        assertEquals(slugPolicy.create("atomic-project"), project.slug());
    }

    @Test
    void publicProjectQueriesUseVisibilityFeaturedFiltersAndStableSorting() {
        Instant currentTime = now();
        Instant tiedPublication = currentTime.minusSeconds(120);
        Project tiedLow = publishedProject(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "tied-low",
                true,
                tiedPublication);
        Project tiedHigh = publishedProject(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "tied-high",
                false,
                tiedPublication);
        Project older = publishedProject(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "older-project",
                true,
                currentTime.minusSeconds(240));
        Project draft = newProject(
                UUID.randomUUID(), "draft-hidden", null, null, true, 0, currentTime);
        Project future = publishedProject(
                UUID.randomUUID(), "future-hidden", true, currentTime.plusSeconds(3_600));
        Project archived = publishedProject(
                UUID.randomUUID(), "archived-hidden", true, currentTime.minusSeconds(60));
        archived.archive(currentTime.minusSeconds(30));
        persistProjects(List.of(tiedLow, tiedHigh, older, draft, future, archived));

        assertEquals(
                List.of("tied-high", "tied-low", "older-project"),
                publicProjectSlugs(currentTime, null, 50, 0));
        assertEquals(
                List.of("tied-low", "older-project"),
                publicProjectSlugs(currentTime, true, 50, 0));
        assertEquals(
                List.of("tied-high"),
                publicProjectSlugs(currentTime, false, 50, 0));
        assertEquals(
                List.of("tied-low"),
                publicProjectSlugs(currentTime, null, 1, 1));
        assertEquals(3, projects.countPublicProjects(currentTime, null));
        assertEquals(2, projects.countPublicProjects(currentTime, true));
        assertTrue(projects.findPublicBySlug("tied-high", currentTime).isPresent());
        assertTrue(projects.findPublicBySlug("draft-hidden", currentTime).isEmpty());
        assertTrue(projects.findPublicBySlug("future-hidden", currentTime).isEmpty());
        assertTrue(projects.findPublicBySlug("archived-hidden", currentTime).isEmpty());
    }

    @Test
    void persistsAndQueriesOnlyVisibleSkillsAndExperiencesInStableOrder() {
        Instant timestamp = now();
        Skill low = new Skill(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Java",
                "Backend",
                "JVM services",
                1,
                true,
                timestamp);
        Skill high = new Skill(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "PostgreSQL",
                "Data",
                null,
                1,
                true,
                timestamp);
        Skill hidden = new Skill(
                UUID.randomUUID(), "Hidden", "Internal", null, 0, false, timestamp);

        Experience recent = new Experience(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Recent organization",
                "Engineer",
                LocalDate.of(2024, 1, 1),
                null,
                "Recent work",
                1,
                true,
                timestamp);
        Experience older = new Experience(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Older organization",
                "Engineer",
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 12, 31),
                "Older work",
                1,
                true,
                timestamp);
        Experience hiddenExperience = new Experience(
                UUID.randomUUID(),
                "Hidden organization",
                "Engineer",
                LocalDate.of(2025, 1, 1),
                null,
                "Hidden work",
                0,
                false,
                timestamp);

        inTransaction(() -> {
            for (Skill skill : List.of(low, high, hidden)) {
                skills.save(skill);
            }
            for (Experience experience : List.of(recent, older, hiddenExperience)) {
                experiences.save(experience);
            }
            entityManager.flush();
        });

        assertEquals(
                List.of("Java", "PostgreSQL"),
                skills.findVisibleSkills().stream().map(Skill::name).toList());
        assertEquals(
                List.of("Recent organization", "Older organization"),
                experiences.findVisibleExperiences().stream().map(Experience::organization).toList());
        assertEquals(0L, skills.findById(low.id()).orElseThrow().version());
        assertEquals(0L, experiences.findById(recent.id()).orElseThrow().version());
    }

    @Test
    void rejectsInvalidExperienceDatesAndNegativeSortOrders() {
        Instant timestamp = now();
        assertThrows(IllegalArgumentException.class, () -> new Skill(
                UUID.randomUUID(), "Java", "Backend", null, -1, true, timestamp));
        assertThrows(IllegalArgumentException.class, () -> new Experience(
                UUID.randomUUID(),
                "Organization",
                "Role",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 1, 31),
                "Summary",
                0,
                true,
                timestamp));
        assertThrows(IllegalArgumentException.class, () -> new Experience(
                UUID.randomUUID(),
                "Organization",
                "Role",
                LocalDate.of(2024, 1, 1),
                null,
                "Summary",
                -1,
                true,
                timestamp));
    }

    @Test
    void repositoriesExposeNoArbitrarySortOrEntityPageContract() {
        for (Class<?> repository : List.of(
                PortfolioProfileRepository.class,
                ProjectRepository.class,
                SkillRepository.class,
                ExperienceRepository.class)) {
            for (Method method : repository.getDeclaredMethods()) {
                assertFalse(Page.class.isAssignableFrom(method.getReturnType()), method.toString());
                assertTrue(Arrays.stream(method.getParameterTypes())
                        .noneMatch(type -> type == Sort.class || type == Pageable.class), method.toString());
            }
        }
        for (Class<?> entity : List.of(
                PortfolioProfile.class, Project.class, Skill.class, Experience.class)) {
            assertTrue(Arrays.stream(entity.getMethods())
                    .noneMatch(method -> method.getName().startsWith("set")), entity.getName());
        }
    }

    private Project publishedProject(UUID id, String slug, boolean featured, Instant publishedAt) {
        Project project = newProject(id, slug, null, null, featured, 0, publishedAt.minusSeconds(1));
        project.publish(publishedAt);
        return project;
    }

    private Project newProject(
            UUID id,
            String slug,
            String projectUrl,
            String repositoryUrl,
            boolean featured,
            int sortOrder,
            Instant timestamp) {
        return new Project(
                id,
                slugPolicy.create(slug),
                "Title " + slug,
                "Summary " + slug,
                "Description " + slug,
                projectUrl,
                repositoryUrl,
                featured,
                sortOrder,
                timestamp);
    }

    private void persistProjects(List<Project> projectValues) {
        inTransaction(() -> {
            projectValues.forEach(projects::save);
            entityManager.flush();
        });
    }

    private List<String> publicProjectSlugs(
            Instant now,
            Boolean featured,
            int limit,
            long offset) {
        return projects.findPublicProjects(now, featured, limit, offset).stream()
                .map(project -> project.slug().value())
                .toList();
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

}
