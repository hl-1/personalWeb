package com.studystack.admin.web.portfolio;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import com.studystack.identity.infrastructure.security.StudyStackPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class AdminProjectApiIntegrationTest {

    private static final String PROJECTS_PATH = "/api/v1/admin/portfolio/projects";
    private static final String SECRET_DESCRIPTION = "# Private project description";

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

    private OAuth2AuthenticationToken adminAuthentication;
    private OAuth2AuthenticationToken userAuthentication;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                truncate table admin_audit_log, portfolio_project,
                    identity_external_identity, identity_user_account cascade
                """);
        UserAccount admin = persistUser("admin", AccountStatus.ACTIVE);
        UserAccount user = persistUser("user", AccountStatus.ACTIVE);
        adminAuthentication = authenticationFor(admin, "70002");
        userAuthentication = authenticationFor(user, "70001");
    }

    @Test
    void protectsAdminRoutesAndRequiresCsrfForWrites() throws Exception {
        mockMvc.perform(get(PROJECTS_PATH))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROJECTS_PATH).with(authentication(userAuthentication)))
                .andExpect(status().isForbidden());
        mockMvc.perform(admin(get(PROJECTS_PATH), false))
                .andExpect(status().isOk());

        mockMvc.perform(post(PROJECTS_PATH)
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectRequest("csrf-project"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PROJECTS_PATH)
                        .with(authentication(adminAuthentication))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectRequest("csrf-project"))))
                .andExpect(status().isForbidden());
        assertEquals(0, auditCount());
    }

    @Test
    void createsReadsUpdatesAndDeletesWithOneAuditPerMutation() throws Exception {
        MvcResult created = mockMvc.perform(admin(post(PROJECTS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectRequest("managed-project"))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value("managed-project"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.descriptionMarkdown").value(SECRET_DESCRIPTION))
                .andExpect(jsonPath("$.projectUrl").value("https://example.com/managed-project"))
                .andExpect(jsonPath("$.featured").value(true))
                .andExpect(jsonPath("$.sortOrder").value(3))
                .andExpect(jsonPath("$.version").isNumber())
                .andReturn();
        Map<String, Object> createdBody = body(created);
        String id = (String) createdBody.get("id");
        long version = ((Number) createdBody.get("version")).longValue();
        assertEquals(PROJECTS_PATH + "/" + id, created.getResponse().getHeader("Location"));
        assertAudit(1, "CREATE", id, version);

        mockMvc.perform(admin(get(PROJECTS_PATH + "/{id}", id), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(14)))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value(version))
                .andExpect(jsonPath("$.entity").doesNotExist())
                .andExpect(jsonPath("$.audit").doesNotExist());
        assertEquals(1, auditCount());

        Map<String, Object> update = projectRequest("managed-project-renamed");
        update.put("title", "Updated project");
        update.put("projectUrl", null);
        update.put("featured", false);
        update.put("sortOrder", 0);
        update.put("version", version);
        MvcResult updated = mockMvc.perform(admin(put(PROJECTS_PATH + "/{id}", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated project"))
                .andExpect(jsonPath("$.projectUrl").doesNotExist())
                .andExpect(jsonPath("$.featured").value(false))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andReturn();
        long updatedVersion = ((Number) body(updated).get("version")).longValue();
        assertAudit(2, "UPDATE", id, updatedVersion);

        mockMvc.perform(admin(delete(PROJECTS_PATH + "/{id}", id)
                        .param("version", Long.toString(updatedVersion)), true))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertAudit(3, "DELETE", id, null);
    }

    @Test
    void publishesAndArchivesWithReturnedVersionsAndRejectsInvalidStates() throws Exception {
        Map<String, Object> created = createProject("stateful-project");
        String id = (String) created.get("id");
        long draftVersion = ((Number) created.get("version")).longValue();

        MvcResult published = mockMvc.perform(admin(post(PROJECTS_PATH + "/{id}/publish", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", draftVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(draftVersion + 1))
                .andReturn();
        long publishedVersion = ((Number) body(published).get("version")).longValue();
        assertAudit(2, "PUBLISH", id, publishedVersion);

        expectProblem(admin(delete(PROJECTS_PATH + "/{id}", id)
                        .param("version", Long.toString(publishedVersion)), true),
                409, "draft_delete_only");
        assertEquals(2, auditCount());

        MvcResult archived = mockMvc.perform(admin(post(PROJECTS_PATH + "/{id}/archive", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", publishedVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.version").value(publishedVersion + 1))
                .andReturn();
        long archivedVersion = ((Number) body(archived).get("version")).longValue();
        assertAudit(3, "ARCHIVE", id, archivedVersion);

        expectProblem(admin(post(PROJECTS_PATH + "/{id}/publish", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", archivedVersion))),
                409, "invalid_state_transition");
        assertEquals(3, auditCount());
    }

    @Test
    void listsWithFixedPaginationFiltersAndNoAudit() throws Exception {
        createProject("first-project");
        createProject("second-project");
        int mutationAudits = auditCount();

        mockMvc.perform(admin(get(PROJECTS_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].*", hasSize(10)))
                .andExpect(jsonPath("$.items[0].descriptionMarkdown").doesNotExist())
                .andExpect(jsonPath("$.items[0].version").isNumber());

        mockMvc.perform(admin(get(PROJECTS_PATH)
                        .param("page", "1")
                        .param("size", "1")
                        .param("status", "DRAFT")
                        .param("query", " PROJECT "), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)));
        assertEquals(mutationAudits, auditCount());
    }

    @Test
    void rejectsInvalidParametersAndBodiesWithoutAudit() throws Exception {
        for (String path : List.of(
                PROJECTS_PATH + "?page=-1",
                PROJECTS_PATH + "?size=0",
                PROJECTS_PATH + "?size=101",
                PROJECTS_PATH + "?status=draft",
                PROJECTS_PATH + "/not-a-uuid")) {
            expectProblem(admin(get(path), false), 400, "validation_failed");
        }
        expectProblem(admin(get(PROJECTS_PATH).param("query", "x".repeat(101)), false),
                400, "validation_failed");

        Map<String, Object> invalidBody = projectRequest("invalid-body");
        invalidBody.put("title", " ");
        MvcResult validation = expectProblem(admin(post(PROJECTS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidBody)),
                400, "validation_failed");
        assertFalse(validation.getResponse().getContentAsString().contains(SECRET_DESCRIPTION));
        assertTrue(body(validation).containsKey("fieldErrors"));

        Map<String, Object> unsafeUrl = projectRequest("unsafe-url");
        unsafeUrl.put("projectUrl", "http://example.com/project");
        MvcResult invalidUrl = expectProblem(admin(post(PROJECTS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unsafeUrl)),
                400, "validation_failed");
        Map<String, Object> invalidUrlBody = body(invalidUrl);
        assertTrue(invalidUrlBody.containsKey("fieldErrors"));
        assertTrue(((Map<?, ?>) invalidUrlBody.get("fieldErrors")).containsKey("projectUrl"));
        assertEquals(0, auditCount());
    }

    @Test
    void returnsStableSanitizedNotFoundAndConflictProblemsWithoutAuditingFailures() throws Exception {
        Map<String, Object> created = createProject("unique-project");
        String id = (String) created.get("id");
        long version = ((Number) created.get("version")).longValue();
        int auditsBeforeFailures = auditCount();

        MvcResult duplicate = expectProblem(admin(post(PROJECTS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectRequest("unique-project"))),
                409, "duplicate_slug");
        assertFalse(duplicate.getResponse().getContentAsString().contains(SECRET_DESCRIPTION));

        Map<String, Object> staleUpdate = projectRequest("unique-project");
        staleUpdate.put("version", version + 20);
        expectProblem(admin(put(PROJECTS_PATH + "/{id}", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(staleUpdate)),
                409, "stale_version");

        expectProblem(admin(get(PROJECTS_PATH + "/{id}", UUID.randomUUID()), false),
                404, "not_found");
        assertEquals(auditsBeforeFailures, auditCount());
    }

    private Map<String, Object> createProject(String slug) throws Exception {
        MvcResult result = mockMvc.perform(admin(post(PROJECTS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(projectRequest(slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result);
    }

    private LinkedHashMap<String, Object> projectRequest(String slug) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("slug", slug);
        request.put("title", "Title " + slug);
        request.put("summary", "Summary " + slug);
        request.put("descriptionMarkdown", SECRET_DESCRIPTION);
        request.put("projectUrl", "https://example.com/" + slug);
        request.put("repositoryUrl", null);
        request.put("featured", true);
        request.put("sortOrder", 3);
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
                .andExpect(jsonPath("$.detail", not(containsString(SECRET_DESCRIPTION))))
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
                account.id(),
                providerSubject,
                account.login(),
                account.displayName(),
                account.avatarUrl());
        return new OAuth2AuthenticationToken(
                principal,
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                "github");
    }

    private UserAccount persistUser(String prefix, AccountStatus status) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        return users.save(new UserAccount(
                id,
                prefix + "-" + id,
                prefix,
                null,
                status,
                now,
                now,
                now));
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

    private void assertAudit(int expectedCount, String action, String resourceId, Long resourceVersion) {
        assertEquals(expectedCount, auditCount());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select action, resource_type, resource_id, resource_version
                from admin_audit_log
                order by occurred_at desc, id desc
                limit 1
                """);
        assertEquals(action, audit.get("action"));
        assertEquals("PROJECT", audit.get("resource_type"));
        assertEquals(UUID.fromString(resourceId), audit.get("resource_id"));
        assertEquals(resourceVersion, audit.get("resource_version"));
    }
}
