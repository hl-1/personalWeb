package com.studystack.admin.web.portfolio;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import com.studystack.identity.infrastructure.security.StudyStackPrincipal;
import com.studystack.portfolio.domain.PortfolioProfile;
import com.studystack.portfolio.domain.PortfolioProfileRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "studystack.identity.security.admin-github-ids=70002",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class AdminPortfolioApiIntegrationTest {

    private static final String PROFILE_PATH = "/api/v1/admin/portfolio/profile";
    private static final String SKILLS_PATH = "/api/v1/admin/portfolio/skills";
    private static final String EXPERIENCES_PATH = "/api/v1/admin/portfolio/experiences";
    private static final String SECRET_MARKDOWN = "# Private portfolio content";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserAccountRepository users;

    @MockitoSpyBean
    PortfolioProfileRepository profileRepository;

    private OAuth2AuthenticationToken adminAuthentication;
    private OAuth2AuthenticationToken userAuthentication;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                truncate table admin_audit_log, portfolio_profile, portfolio_skill, portfolio_experience,
                    identity_external_identity, identity_user_account cascade
                """);
        UserAccount admin = persistUser("admin", AccountStatus.ACTIVE);
        UserAccount user = persistUser("user", AccountStatus.ACTIVE);
        adminAuthentication = authenticationFor(admin, "70002");
        userAuthentication = authenticationFor(user, "70001");
    }

    @Test
    void protectsAllRoutesAndRequiresCsrfForWrites() throws Exception {
        for (String path : List.of(PROFILE_PATH, SKILLS_PATH, EXPERIENCES_PATH)) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).with(authentication(userAuthentication))).andExpect(status().isForbidden());
        }

        mockMvc.perform(put(PROFILE_PATH)
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(profileRequest(null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(SKILLS_PATH)
                        .with(authentication(adminAuthentication))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skillRequest("Java", 0, true))))
                .andExpect(status().isForbidden());
        assertEquals(0, auditCount());
    }

    @Test
    void createsAndUpdatesTheProfileAndDoesNotAuditReadsOrFailures() throws Exception {
        expectProblem(admin(get(PROFILE_PATH), false), 404, "not_found");
        assertEquals(0, auditCount());

        MvcResult created = mockMvc.perform(admin(put(PROFILE_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(profileRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("StudyStack Author"))
                .andExpect(jsonPath("$.bioMarkdown").value(SECRET_MARKDOWN))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        long version = ((Number) body(created).get("version")).longValue();
        assertAudit(1, "CREATE", "PROFILE", new UUID(0, 1), version);

        mockMvc.perform(admin(get(PROFILE_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(8)))
                .andExpect(jsonPath("$.displayName").value("StudyStack Author"));
        assertEquals(1, auditCount());

        Map<String, Object> update = profileRequest(version);
        update.put("displayName", "Updated Author");
        update.put("seoDescription", null);
        MvcResult updated = mockMvc.perform(admin(put(PROFILE_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Author"))
                .andExpect(jsonPath("$.seoDescription").doesNotExist())
                .andExpect(jsonPath("$.version").value(version + 1))
                .andReturn();
        assertAudit(2, "UPDATE", "PROFILE", new UUID(0, 1),
                ((Number) body(updated).get("version")).longValue());

        expectProblem(admin(put(PROFILE_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(profileRequest(version))),
                409,
                "stale_version");
        Map<String, Object> invalid = profileRequest(version + 1);
        invalid.put("displayName", " ");
        MvcResult validation = expectProblem(admin(put(PROFILE_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)),
                400,
                "validation_failed");
        assertTrue(body(validation).containsKey("fieldErrors"));
        assertFalse(validation.getResponse().getContentAsString().contains(SECRET_MARKDOWN));
        assertEquals(2, auditCount());
    }

    @Test
    void mapsConcurrentSingletonInsertToStaleVersionAndAuditsOnlyTheWinner() throws Exception {
        CountDownLatch bothObservedMissing = new CountDownLatch(2);
        doAnswer(invocation -> {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "select count(*) from portfolio_profile", Integer.class));
            bothObservedMissing.countDown();
            if (!bothObservedMissing.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Both profile creations did not reach the insert barrier");
            }
            return Optional.empty();
        }).when(profileRepository).findById(PortfolioProfile.SINGLETON_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(this::putInitialProfile);
            Future<MvcResult> second = executor.submit(this::putInitialProfile);
            List<MvcResult> results = List.of(first.get(), second.get());

            assertEquals(1, results.stream()
                    .filter(result -> result.getResponse().getStatus() == 200)
                    .count());
            MvcResult conflict = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertEquals("stale_version", body(conflict).get("code"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from portfolio_profile", Integer.class));
            assertAudit(1, "CREATE", "PROFILE", new UUID(0, 1), 0L);
        } finally {
            executor.shutdownNow();
            reset(profileRepository);
        }
    }

    @Test
    void managesSkillsWithStableOrderDuplicatesValidationConflictsAndAudit() throws Exception {
        Map<String, Object> later = createSkill("Java", 2, false);
        Map<String, Object> earlierDuplicate = createSkill("Java", 1, true);
        String laterId = (String) later.get("id");
        String earlierId = (String) earlierDuplicate.get("id");
        assertAudit(2, "CREATE", "SKILL", UUID.fromString(earlierId),
                ((Number) earlierDuplicate.get("version")).longValue());

        mockMvc.perform(admin(get(SKILLS_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(earlierId))
                .andExpect(jsonPath("$[1].id").value(laterId));
        assertEquals(2, auditCount());

        Map<String, Object> update = skillRequest("Kotlin", 0, true);
        update.put("version", later.get("version"));
        MvcResult updated = mockMvc.perform(admin(put(SKILLS_PATH + "/{id}", laterId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kotlin"))
                .andExpect(jsonPath("$.visible").value(true))
                .andReturn();
        long updatedVersion = ((Number) body(updated).get("version")).longValue();
        assertAudit(3, "UPDATE", "SKILL", UUID.fromString(laterId), updatedVersion);

        expectProblem(admin(put(SKILLS_PATH + "/{id}", laterId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)),
                409,
                "stale_version");
        Map<String, Object> invalid = skillRequest("Invalid", -1, true);
        expectProblem(admin(post(SKILLS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)),
                400,
                "validation_failed");
        Map<String, Object> missingSortOrder = skillRequest("Missing order", 0, true);
        missingSortOrder.remove("sortOrder");
        expectProblem(admin(post(SKILLS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(missingSortOrder)),
                400,
                "validation_failed");
        Map<String, Object> missingVisible = skillRequest("Missing visibility", 0, true);
        missingVisible.put("version", updatedVersion);
        missingVisible.put("visible", null);
        expectProblem(admin(put(SKILLS_PATH + "/{id}", laterId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(missingVisible)),
                400,
                "validation_failed");
        assertEquals(3, auditCount());

        mockMvc.perform(admin(delete(SKILLS_PATH + "/{id}", laterId)
                        .param("version", Long.toString(updatedVersion)), true))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertAudit(4, "DELETE", "SKILL", UUID.fromString(laterId), null);
    }

    @Test
    void managesExperiencesWithDatesMarkdownVisibilityConflictsAndAudit() throws Exception {
        Map<String, Object> later = createExperience("Later organization", 2, false);
        Map<String, Object> earlier = createExperience("Earlier organization", 1, true);
        String laterId = (String) later.get("id");
        String earlierId = (String) earlier.get("id");

        mockMvc.perform(admin(get(EXPERIENCES_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(earlierId))
                .andExpect(jsonPath("$[1].id").value(laterId));
        assertEquals(2, auditCount());

        Map<String, Object> update = experienceRequest("Updated organization", 0, true);
        update.put("version", later.get("version"));
        MvcResult updated = mockMvc.perform(admin(put(EXPERIENCES_PATH + "/{id}", laterId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization").value("Updated organization"))
                .andExpect(jsonPath("$.summaryMarkdown").value(SECRET_MARKDOWN))
                .andReturn();
        long updatedVersion = ((Number) body(updated).get("version")).longValue();
        assertAudit(3, "UPDATE", "EXPERIENCE", UUID.fromString(laterId), updatedVersion);

        expectProblem(admin(put(EXPERIENCES_PATH + "/{id}", laterId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)),
                409,
                "stale_version");
        Map<String, Object> invalidDate = experienceRequest("Invalid dates", 0, true);
        invalidDate.put("startDate", LocalDate.of(2025, 1, 1));
        invalidDate.put("endDate", LocalDate.of(2024, 1, 1));
        expectProblem(admin(post(EXPERIENCES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidDate)),
                400,
                "validation_failed");
        Map<String, Object> tooLong = experienceRequest("Invalid markdown", 0, true);
        tooLong.put("summaryMarkdown", "x".repeat(20_001));
        MvcResult validation = expectProblem(admin(post(EXPERIENCES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tooLong)),
                400,
                "validation_failed");
        assertTrue(body(validation).containsKey("fieldErrors"));
        assertFalse(validation.getResponse().getContentAsString().contains("x".repeat(100)));
        Map<String, Object> missingSortOrder = experienceRequest("Missing order", 0, true);
        missingSortOrder.put("sortOrder", null);
        expectProblem(admin(post(EXPERIENCES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(missingSortOrder)),
                400,
                "validation_failed");
        Map<String, Object> missingVisible = experienceRequest("Missing visibility", 0, true);
        missingVisible.put("version", updatedVersion);
        missingVisible.remove("visible");
        expectProblem(admin(put(EXPERIENCES_PATH + "/{id}", laterId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(missingVisible)),
                400,
                "validation_failed");
        assertEquals(3, auditCount());

        mockMvc.perform(admin(delete(EXPERIENCES_PATH + "/{id}", laterId)
                        .param("version", Long.toString(updatedVersion)), true))
                .andExpect(status().isNoContent());
        assertAudit(4, "DELETE", "EXPERIENCE", UUID.fromString(laterId), null);
    }

    private Map<String, Object> createSkill(String name, int sortOrder, boolean visible) throws Exception {
        MvcResult result = mockMvc.perform(admin(post(SKILLS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(skillRequest(name, sortOrder, visible))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(SKILLS_PATH + "/")))
                .andReturn();
        return body(result);
    }

    private MvcResult putInitialProfile() throws Exception {
        return mockMvc.perform(admin(put(PROFILE_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(profileRequest(null))))
                .andReturn();
    }

    private Map<String, Object> createExperience(String organization, int sortOrder, boolean visible)
            throws Exception {
        MvcResult result = mockMvc.perform(admin(post(EXPERIENCES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(experienceRequest(organization, sortOrder, visible))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(EXPERIENCES_PATH + "/")))
                .andReturn();
        return body(result);
    }

    private LinkedHashMap<String, Object> profileRequest(Long version) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("displayName", "StudyStack Author");
        request.put("headline", "Platform engineer");
        request.put("bioMarkdown", SECRET_MARKDOWN);
        request.put("seoDescription", "Portfolio profile");
        request.put("version", version);
        return request;
    }

    private LinkedHashMap<String, Object> skillRequest(String name, int sortOrder, boolean visible) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("category", "Backend");
        request.put("summary", "Skill summary");
        request.put("sortOrder", sortOrder);
        request.put("visible", visible);
        return request;
    }

    private LinkedHashMap<String, Object> experienceRequest(
            String organization,
            int sortOrder,
            boolean visible) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("organization", organization);
        request.put("role", "Engineer");
        request.put("startDate", LocalDate.of(2020, 1, 1));
        request.put("endDate", LocalDate.of(2021, 1, 1));
        request.put("summaryMarkdown", SECRET_MARKDOWN);
        request.put("sortOrder", sortOrder);
        request.put("visible", visible);
        return request;
    }

    private MvcResult expectProblem(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:studystack:problem:" + expectedCode.replace('_', '-')))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").isNotEmpty())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.detail", not(containsString(SECRET_MARKDOWN))))
                .andReturn();
    }

    private MockHttpServletRequestBuilder admin(
            MockHttpServletRequestBuilder request,
            boolean includeCsrf) {
        request.with(authentication(adminAuthentication));
        if (includeCsrf) {
            request.with(csrf());
        }
        return request;
    }

    private OAuth2AuthenticationToken authenticationFor(UserAccount account, String providerSubject) {
        StudyStackPrincipal principal = new StudyStackPrincipal(
                account.id(), providerSubject, account.login(), account.displayName(), account.avatarUrl());
        return new OAuth2AuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "github");
    }

    private UserAccount persistUser(String prefix, AccountStatus status) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        return users.save(new UserAccount(
                id, prefix + "-" + id, prefix, null, status, now, now, now));
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                new TypeReference<>() {
                });
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject("select count(*) from admin_audit_log", Integer.class);
    }

    private void assertAudit(
            int expectedCount,
            String action,
            String resourceType,
            UUID resourceId,
            Long resourceVersion) {
        assertEquals(expectedCount, auditCount());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select action, resource_type, resource_id, resource_version
                from admin_audit_log
                order by occurred_at desc, id desc
                limit 1
                """);
        assertEquals(action, audit.get("action"));
        assertEquals(resourceType, audit.get("resource_type"));
        assertEquals(resourceId, audit.get("resource_id"));
        assertEquals(resourceVersion, audit.get("resource_version"));
    }
}
