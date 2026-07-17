package com.studystack.portfolio.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class PublicPortfolioApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired MockMvc mockMvc;
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
    void servesAnonymousProfileProjectsSkillsAndExperiencesWithFieldWhitelists() throws Exception {
        persistFixture();

        mockMvc.perform(get("/api/v1/portfolio/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.displayName").value("StudyStack Author"))
                .andExpect(jsonPath("$.bioHtml", containsString("<h1>About</h1>")))
                .andExpect(jsonPath("$.bioMarkdown").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        mockMvc.perform(get("/api/v1/portfolio/projects").param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].*", hasSize(7)))
                .andExpect(jsonPath("$.items[0].slug").value("public-project"))
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].sortOrder").doesNotExist());

        mockMvc.perform(get("/api/v1/portfolio/projects/public-project"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(11)))
                .andExpect(jsonPath("$.descriptionHtml", containsString("<h1>Project</h1>")))
                .andExpect(jsonPath("$.descriptionHtml", not(containsString("script"))))
                .andExpect(jsonPath("$.canonicalPath").value("/projects/public-project"))
                .andExpect(jsonPath("$.descriptionMarkdown").doesNotExist());

        mockMvc.perform(get("/api/v1/portfolio/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(4)))
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[0].sortOrder").doesNotExist());

        mockMvc.perform(get("/api/v1/portfolio/experiences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(6)))
                .andExpect(jsonPath("$[0].organization").value("StudyStack"))
                .andExpect(jsonPath("$[0].summaryHtml", containsString("<strong>Built</strong>")))
                .andExpect(jsonPath("$[0].summaryMarkdown").doesNotExist());
    }

    @Test
    void returnsStableProblemsAndEmptyCollections() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/profile"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.type").value("urn:studystack:problem:portfolio-not-found"))
                .andExpect(jsonPath("$.code").value("portfolio_not_found"));
        mockMvc.perform(get("/api/v1/portfolio/projects/missing-project"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("portfolio_not_found"));
        mockMvc.perform(get("/api/v1/portfolio/projects?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        mockMvc.perform(get("/api/v1/portfolio/projects?size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        mockMvc.perform(get("/api/v1/portfolio/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/portfolio/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/portfolio/experiences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void persistFixture() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Project project = new Project(
                UUID.randomUUID(), slugPolicy.create("public-project"), "Public project", "Summary",
                "# Project\n\n<script>secret</script>", "https://example.com/project", null,
                true, 1, now.minusSeconds(120));
        project.publish(now.minusSeconds(60));
        persist(() -> {
            profiles.save(new PortfolioProfile(
                    "StudyStack Author", "Platform engineer", "# About", "Profile SEO", now));
            projects.save(project);
            skills.save(new Skill(UUID.randomUUID(), "Java", "Backend", null, 1, true, now));
            experiences.save(new Experience(
                    UUID.randomUUID(), "StudyStack", "Engineer", LocalDate.of(2024, 1, 1), null,
                    "**Built** safely", 1, true, now));
        });
    }

    private void persist(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            action.run();
            entityManager.flush();
            entityManager.clear();
        });
    }
}
