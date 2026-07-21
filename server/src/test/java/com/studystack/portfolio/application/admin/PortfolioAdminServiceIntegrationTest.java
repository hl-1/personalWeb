package com.studystack.portfolio.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.AdminException;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Experience;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Failure;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Profile;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Skill;
import java.time.LocalDate;
import java.util.List;
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
class PortfolioAdminServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    ProfileAdminService profiles;

    @Autowired
    SkillAdminService skills;

    @Autowired
    ExperienceAdminService experiences;

    @Autowired
    PublicPortfolioQuery publicPortfolio;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table portfolio_profile, portfolio_skill, portfolio_experience");
    }

    @Test
    void createsUpdatesAndProtectsTheSingletonProfile() {
        assertFailure(Failure.NOT_FOUND, profiles::find);

        Profile created = profiles.upsert(profileCommand("First profile", null));
        assertEquals(0L, created.version());
        assertEquals("First profile", created.displayName());
        assertEquals("First profile", publicPortfolio.findProfile().displayName());

        Profile updated = profiles.upsert(new ProfileAdminService.Command(
                "Updated profile",
                "Updated headline",
                "# Updated biography",
                null,
                created.version()));
        assertEquals(1L, updated.version());
        assertEquals("Updated profile", profiles.find().displayName());

        assertFailure(Failure.STALE_VERSION, () -> profiles.upsert(profileCommand("Stale profile", 0L)));
        assertThrows(IllegalArgumentException.class, () -> profiles.upsert(new ProfileAdminService.Command(
                "Invalid profile",
                "Invalid headline",
                "x".repeat(50_001),
                null,
                updated.version())));
        assertEquals(updated, profiles.find());
    }

    @Test
    void managesSkillsWithStableOrderVisibilityDuplicatesAndConcurrency() throws Exception {
        Skill hidden = skills.create(new SkillAdminService.Create(
                "Java", "Backend", null, 2, false));
        Skill visibleDuplicate = skills.create(new SkillAdminService.Create(
                "Java", "Backend", "Visible duplicate", 1, true));

        assertEquals(List.of(visibleDuplicate.id(), hidden.id()),
                skills.list().stream().map(Skill::id).toList());
        assertEquals(List.of(visibleDuplicate.id()),
                publicPortfolio.findSkills().stream().map(view -> view.id()).toList());

        Skill updated = skills.update(hidden.id(), new SkillAdminService.Update(
                "Kotlin", "Backend", "Updated", 0, true, hidden.version()));
        assertEquals(1L, updated.version());
        assertEquals(List.of(updated.id(), visibleDuplicate.id()),
                skills.list().stream().map(Skill::id).toList());

        assertThrows(IllegalArgumentException.class, () -> skills.create(
                new SkillAdminService.Create("Invalid", "Backend", null, -1, true)));
        assertFailure(Failure.STALE_VERSION, () -> skills.update(hidden.id(), new SkillAdminService.Update(
                "Stale", "Backend", null, 0, true, hidden.version())));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> updateSkillAfterSignal(start, updated, "First"));
            Future<Object> second = executor.submit(() -> updateSkillAfterSignal(start, updated, "Second"));
            start.countDown();
            assertOneSuccessAndOneStale(first.get(), second.get(), Skill.class);
        } finally {
            executor.shutdownNow();
        }

        Skill latest = skills.list().get(0);
        skills.delete(latest.id(), latest.version());
        assertFailure(Failure.NOT_FOUND, () -> skills.update(latest.id(), new SkillAdminService.Update(
                "Missing", "Backend", null, 0, true, latest.version())));
    }

    @Test
    void managesExperiencesWithDatesMarkdownVisibilityOrderAndConcurrency() throws Exception {
        Experience hidden = experiences.create(new ExperienceAdminService.Create(
                "Later organization",
                "Engineer",
                LocalDate.of(2024, 1, 1),
                null,
                "Hidden summary",
                2,
                false));
        Experience visible = experiences.create(new ExperienceAdminService.Create(
                "Earlier organization",
                "Developer",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2021, 1, 1),
                "Visible summary",
                1,
                true));

        assertEquals(List.of(visible.id(), hidden.id()),
                experiences.list().stream().map(Experience::id).toList());
        assertEquals(List.of(visible.id()),
                publicPortfolio.findExperiences().stream().map(view -> view.id()).toList());

        Experience updated = experiences.update(hidden.id(), new ExperienceAdminService.Update(
                "Updated organization",
                "Senior engineer",
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 1, 1),
                "# Updated summary",
                0,
                true,
                hidden.version()));
        assertEquals(1L, updated.version());

        assertThrows(IllegalArgumentException.class, () -> experiences.create(
                new ExperienceAdminService.Create(
                        "Invalid date",
                        "Role",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2024, 1, 1),
                        "Summary",
                        0,
                        true)));
        assertThrows(IllegalArgumentException.class, () -> experiences.update(
                updated.id(),
                new ExperienceAdminService.Update(
                        updated.organization(),
                        updated.role(),
                        updated.startDate(),
                        updated.endDate(),
                        "x".repeat(20_001),
                        updated.sortOrder(),
                        updated.visible(),
                        updated.version())));
        assertEquals(updated, experiences.list().get(0));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> updateExperienceAfterSignal(start, updated, "First"));
            Future<Object> second = executor.submit(() -> updateExperienceAfterSignal(start, updated, "Second"));
            start.countDown();
            assertOneSuccessAndOneStale(first.get(), second.get(), Experience.class);
        } finally {
            executor.shutdownNow();
        }

        Experience latest = experiences.list().get(0);
        experiences.delete(latest.id(), latest.version());
        assertFailure(Failure.NOT_FOUND, () -> experiences.delete(latest.id(), latest.version()));
    }

    private ProfileAdminService.Command profileCommand(String displayName, Long version) {
        return new ProfileAdminService.Command(
                displayName, "Platform engineer", "# Biography", "Profile description", version);
    }

    private Object updateSkillAfterSignal(CountDownLatch start, Skill skill, String suffix)
            throws InterruptedException {
        start.await();
        try {
            return skills.update(skill.id(), new SkillAdminService.Update(
                    suffix + " skill", skill.category(), skill.summary(), skill.sortOrder(), skill.visible(), skill.version()));
        } catch (AdminException exception) {
            return exception;
        }
    }

    private Object updateExperienceAfterSignal(CountDownLatch start, Experience experience, String suffix)
            throws InterruptedException {
        start.await();
        try {
            return experiences.update(experience.id(), new ExperienceAdminService.Update(
                    suffix + " organization",
                    experience.role(),
                    experience.startDate(),
                    experience.endDate(),
                    experience.summaryMarkdown(),
                    experience.sortOrder(),
                    experience.visible(),
                    experience.version()));
        } catch (AdminException exception) {
            return exception;
        }
    }

    private void assertOneSuccessAndOneStale(Object first, Object second, Class<?> successType) {
        List<Object> results = List.of(first, second);
        assertEquals(1, results.stream().filter(successType::isInstance).count());
        AdminException failure = assertInstanceOf(
                AdminException.class,
                results.stream().filter(AdminException.class::isInstance).findFirst().orElseThrow());
        assertEquals(Failure.STALE_VERSION, failure.failure());
    }

    private void assertFailure(Failure expected, Runnable action) {
        AdminException failure = assertThrows(AdminException.class, action::run);
        assertEquals(expected, failure.failure());
    }
}
