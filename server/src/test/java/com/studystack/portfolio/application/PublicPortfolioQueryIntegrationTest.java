package com.studystack.portfolio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.portfolio.domain.Experience;
import com.studystack.portfolio.domain.ExperienceRepository;
import com.studystack.portfolio.domain.PortfolioProfile;
import com.studystack.portfolio.domain.PortfolioProfileRepository;
import com.studystack.portfolio.domain.Project;
import com.studystack.portfolio.domain.ProjectRepository;
import com.studystack.portfolio.domain.Skill;
import com.studystack.portfolio.domain.SkillRepository;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.EntityManager;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        "springdoc.swagger-ui.enabled=false"
})
class PublicPortfolioQueryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired PublicPortfolioQuery query;
    @Autowired PortfolioProfileRepository profiles;
    @Autowired ProjectRepository projects;
    @Autowired SkillRepository skills;
    @Autowired ExperienceRepository experiences;
    @Autowired SlugPolicy slugPolicy;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPortfolioData() {
        jdbcTemplate.execute(
                "truncate table portfolio_project, portfolio_skill, portfolio_experience, portfolio_profile");
    }

    @Test
    void returnsSanitizedProfileOrStableNotFound() {
        PortfolioNotFoundException missing = assertThrows(
                PortfolioNotFoundException.class, query::findProfile);
        assertEquals(PortfolioNotFoundException.CODE, missing.code());

        persist(() -> profiles.save(new PortfolioProfile(
                "StudyStack Author",
                "Platform engineer",
                "# About\n\n<script>secret</script>",
                "Public profile",
                now())));

        PortfolioProfileView profile = query.findProfile();
        assertEquals("StudyStack Author", profile.displayName());
        assertTrue(profile.bioHtml().contains("<h1>About</h1>"));
        assertFalse(profile.bioHtml().contains("script"));
        assertEquals(Set.of("displayName", "headline", "bioHtml", "seoDescription"),
                components(PortfolioProfileView.class));
    }

    @Test
    void pagesOnlyCurrentPublishedProjectsWithFeaturedFilterAndStableOrder() {
        Instant now = now();
        Instant tied = now.minusSeconds(120);
        Project low = publishedProject(id(1), "tied-low", true, tied);
        Project high = publishedProject(id(2), "tied-high", false, tied);
        Project older = publishedProject(id(3), "older-project", true, now.minusSeconds(240));
        Project draft = project(UUID.randomUUID(), "draft-hidden", true, now);
        Project future = publishedProject(UUID.randomUUID(), "future-hidden", true, now.plusSeconds(3_600));
        Project archived = publishedProject(UUID.randomUUID(), "archived-hidden", true, now.minusSeconds(60));
        archived.archive(now.minusSeconds(30));
        persist(() -> List.of(low, high, older, draft, future, archived).forEach(projects::save));

        Page<ProjectSummary> all = query.findProjects(null, null, null);
        assertEquals(0, all.getNumber());
        assertEquals(10, all.getSize());
        assertEquals(3, all.getTotalElements());
        assertEquals(List.of("tied-high", "tied-low", "older-project"),
                all.getContent().stream().map(ProjectSummary::slug).toList());
        assertEquals(List.of("tied-low", "older-project"),
                query.findProjects(0, 50, true).stream().map(ProjectSummary::slug).toList());
        assertEquals(List.of("tied-high"),
                query.findProjects(0, 10, false).stream().map(ProjectSummary::slug).toList());
        assertThrows(IllegalArgumentException.class, () -> query.findProjects(-1, 10, null));
        assertThrows(IllegalArgumentException.class, () -> query.findProjects(0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> query.findProjects(0, 51, null));
    }

    @Test
    void returnsSanitizedProjectDetailAndHidesUnavailableProjects() {
        Instant now = now();
        Project visible = project(id(1), "public-project", true, now.minusSeconds(90));
        visible.publish(now.minusSeconds(60));
        Project draft = project(id(2), "draft-hidden", false, now.minusSeconds(30));
        persist(() -> List.of(visible, draft).forEach(projects::save));

        ProjectDetail detail = query.findProject("  PUBLIC-PROJECT  ");
        assertEquals("/projects/public-project", detail.canonicalPath());
        assertTrue(detail.descriptionHtml().contains("<h1>Project</h1>"));
        assertFalse(detail.descriptionHtml().contains("script"));
        assertEquals("https://example.com/public-project", detail.projectUrl());
        assertEquals(Set.of(
                        "id", "slug", "title", "summary", "featured", "publishedAt", "updatedAt",
                        "descriptionHtml", "projectUrl", "repositoryUrl", "canonicalPath"),
                components(ProjectDetail.class));
        for (String slug : List.of("draft-hidden", "missing-project", "--invalid--")) {
            PortfolioNotFoundException exception = assertThrows(
                    PortfolioNotFoundException.class, () -> query.findProject(slug));
            assertEquals(PortfolioNotFoundException.CODE, exception.code());
            assertFalse(exception.getMessage().contains(slug));
        }
    }

    @Test
    void listsOnlyVisibleSkillsAndExperiencesInRepositoryOrder() {
        Instant timestamp = now();
        persist(() -> {
            skills.save(new Skill(id(1), "Java", "Backend", "JVM services", 1, true, timestamp));
            skills.save(new Skill(id(2), "PostgreSQL", "Data", null, 1, true, timestamp));
            skills.save(new Skill(id(3), "Hidden", "Internal", null, 0, false, timestamp));
            experiences.save(new Experience(
                    id(1), "Recent", "Engineer", LocalDate.of(2024, 1, 1), null,
                    "**Recent** <script>secret</script>", 1, true, timestamp));
            experiences.save(new Experience(
                    id(2), "Older", "Engineer", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31),
                    "Older", 1, true, timestamp));
            experiences.save(new Experience(
                    id(3), "Hidden", "Engineer", LocalDate.of(2025, 1, 1), null,
                    "Hidden secret", 0, false, timestamp));
        });

        assertEquals(List.of("Java", "PostgreSQL"),
                query.findSkills().stream().map(SkillView::name).toList());
        List<ExperienceView> result = query.findExperiences();
        assertEquals(List.of("Recent", "Older"),
                result.stream().map(ExperienceView::organization).toList());
        assertTrue(result.getFirst().summaryHtml().contains("<strong>Recent</strong>"));
        assertFalse(result.getFirst().summaryHtml().contains("script"));
        assertEquals(Set.of("id", "name", "category", "summary"), components(SkillView.class));
        assertEquals(Set.of("id", "organization", "role", "startDate", "endDate", "summaryHtml"),
                components(ExperienceView.class));
    }

    private Project publishedProject(UUID id, String slug, boolean featured, Instant publishedAt) {
        Project project = project(id, slug, featured, publishedAt.minusSeconds(1));
        project.publish(publishedAt);
        return project;
    }

    private Project project(UUID id, String slug, boolean featured, Instant timestamp) {
        return new Project(
                id, slugPolicy.create(slug), "Title " + slug, "Summary " + slug,
                "# Project\n\n<script>secret</script>",
                "https://example.com/" + slug, null, featured, 0, timestamp);
    }

    private Set<String> components(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private UUID id(long value) {
        return new UUID(0, value);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void persist(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            action.run();
            entityManager.flush();
            entityManager.clear();
        });
    }
}
