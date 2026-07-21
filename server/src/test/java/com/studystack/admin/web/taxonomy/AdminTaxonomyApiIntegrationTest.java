package com.studystack.admin.web.taxonomy;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.sql.Timestamp;
import java.time.Instant;
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
class AdminTaxonomyApiIntegrationTest {

    private static final String CATEGORIES_PATH = "/api/v1/admin/categories";
    private static final String TAGS_PATH = "/api/v1/admin/tags";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

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
                truncate table admin_audit_log, content_article_tag, content_article,
                    content_category, content_tag, identity_external_identity,
                    identity_user_account cascade
                """);
        adminAuthentication = authenticationFor(persistUser("admin", AccountStatus.ACTIVE), "70002");
        userAuthentication = authenticationFor(persistUser("user", AccountStatus.ACTIVE), "70001");
    }

    @Test
    void protectsRoutesAndCreatesReadsUpdatesAndDeletesTaxonomiesWithOneAuditPerMutation() throws Exception {
        mockMvc.perform(get(CATEGORIES_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CATEGORIES_PATH).with(authentication(userAuthentication)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(admin(post(CATEGORIES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Java", "java-category"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(CATEGORIES_PATH + "/")))
                .andExpect(jsonPath("$.name").value("Java"))
                .andExpect(jsonPath("$.articleCount").value(0))
                .andReturn();
        Map<String, Object> category = body(created);
        String categoryId = (String) category.get("id");
        long version = ((Number) category.get("version")).longValue();
        assertAudit(1, "CREATE", "CATEGORY", categoryId, version);

        mockMvc.perform(admin(get(CATEGORIES_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(7)))
                .andExpect(jsonPath("$[0].id").value(categoryId));
        assertEquals(1, auditCount());

        MvcResult updated = mockMvc.perform(admin(put(CATEGORIES_PATH + "/{id}", categoryId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Java Updated", "java-updated", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("java-updated"))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andReturn();
        long updatedVersion = ((Number) body(updated).get("version")).longValue();
        assertAudit(2, "UPDATE", "CATEGORY", categoryId, updatedVersion);

        MvcResult tag = mockMvc.perform(admin(post(TAGS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Spring", "spring-tag"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(TAGS_PATH + "/")))
                .andReturn();
        Map<String, Object> tagBody = body(tag);
        String tagId = (String) tagBody.get("id");
        long tagVersion = ((Number) tagBody.get("version")).longValue();
        assertAudit(3, "CREATE", "TAG", tagId, tagVersion);

        mockMvc.perform(admin(get(TAGS_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(tagId));
        MvcResult updatedTag = mockMvc.perform(admin(put(TAGS_PATH + "/{id}", tagId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Spring Updated", "spring-updated", tagVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(tagVersion + 1))
                .andReturn();
        long updatedTagVersion = ((Number) body(updatedTag).get("version")).longValue();
        assertAudit(4, "UPDATE", "TAG", tagId, updatedTagVersion);

        mockMvc.perform(admin(delete(CATEGORIES_PATH + "/{id}", categoryId)
                        .param("version", Long.toString(updatedVersion)), true))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertAudit(5, "DELETE", "CATEGORY", categoryId, null);

        mockMvc.perform(admin(delete(TAGS_PATH + "/{id}", tagId)
                        .param("version", Long.toString(updatedTagVersion)), true))
                .andExpect(status().isNoContent());
        assertAudit(6, "DELETE", "TAG", tagId, null);
    }

    @Test
    void mapsValidationConflictsAndTaxonomyReferencesWithoutAuditingFailures() throws Exception {
        Map<String, Object> created = createCategory("Java", "java-category");
        String categoryId = (String) created.get("id");
        long version = ((Number) created.get("version")).longValue();
        Map<String, Object> createdTag = createTag("Spring", "spring-tag");
        String tagId = (String) createdTag.get("id");
        long tagVersion = ((Number) createdTag.get("version")).longValue();
        int auditCount = auditCount();

        expectProblem(admin(post(CATEGORIES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Java", "another-category"))),
                409, "duplicate_slug");
        expectProblem(admin(post(CATEGORIES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Another", "java-category"))),
                409, "duplicate_slug");
        expectProblem(admin(post(TAGS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Invalid", "not valid"))),
                400, "validation_failed");
        MvcResult shortSlug = expectProblem(admin(post(TAGS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Too short", "ab"))),
                400, "validation_failed");
        assertTrue(body(shortSlug).containsKey("fieldErrors"));
        expectProblem(admin(put(CATEGORIES_PATH + "/{id}", categoryId), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Changed", "changed-category", version + 1))),
                409, "stale_version");

        UUID articleId = insertArticle("referencing-article", categoryId);
        jdbcTemplate.update(
                "insert into content_article_tag (article_id, tag_id) values (?, ?)",
                articleId,
                UUID.fromString(tagId));
        expectProblem(admin(delete(CATEGORIES_PATH + "/{id}", categoryId)
                        .param("version", Long.toString(version)), true),
                409, "taxonomy_in_use");
        expectProblem(admin(delete(TAGS_PATH + "/{id}", tagId)
                        .param("version", Long.toString(tagVersion)), true),
                409, "taxonomy_in_use");
        expectProblem(admin(delete(CATEGORIES_PATH + "/{id}", UUID.randomUUID())
                        .param("version", "0"), true),
                404, "not_found");
        assertEquals(UUID.fromString(categoryId), jdbcTemplate.queryForObject(
                "select category_id from content_article where id = ?", UUID.class, articleId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from content_article_tag where article_id = ? and tag_id = ?",
                Integer.class,
                articleId,
                UUID.fromString(tagId)));
        assertEquals(auditCount, auditCount());
    }

    @Test
    void exposesOnlyFixedRoutesAndRequiresCsrfAndVersionForWrites() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(admin(get(CATEGORIES_PATH + "/{id}", id), false))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(admin(get(TAGS_PATH + "/{id}", id), false))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post(CATEGORIES_PATH)
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("No CSRF", "no-csrf"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(TAGS_PATH)
                        .with(authentication(adminAuthentication))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Bad CSRF", "bad-csrf"))))
                .andExpect(status().isForbidden());
        expectProblem(admin(delete(CATEGORIES_PATH + "/{id}", id), true),
                400, "validation_failed");
        assertEquals(0, auditCount());
    }

    private Map<String, Object> createCategory(String name, String slug) throws Exception {
        return body(mockMvc.perform(admin(post(CATEGORIES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(name, slug))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Map<String, Object> createTag(String name, String slug) throws Exception {
        return body(mockMvc.perform(admin(post(TAGS_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(name, slug))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID insertArticle(String slug, String categoryId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into content_article (
                    id, slug, title, summary, body_markdown, status, category_id, published_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)
                """, id, slug, "Title", "Summary", "Body", UUID.fromString(categoryId), now, now, now);
        return id;
    }

    private LinkedHashMap<String, Object> request(String name, String slug) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("slug", slug);
        return request;
    }

    private LinkedHashMap<String, Object> request(String name, String slug, long version) {
        LinkedHashMap<String, Object> request = request(name, slug);
        request.put("version", version);
        return request;
    }

    private MvcResult expectProblem(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.detail", not(containsString("java-category"))))
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
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<>() {
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
            String resourceId,
            Long resourceVersion) {
        assertEquals(expectedCount, auditCount());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select action, resource_type, resource_id, resource_version
                from admin_audit_log order by occurred_at desc, id desc limit 1
                """);
        assertEquals(action, audit.get("action"));
        assertEquals(resourceType, audit.get("resource_type"));
        assertEquals(UUID.fromString(resourceId), audit.get("resource_id"));
        assertEquals(resourceVersion, audit.get("resource_version"));
    }
}
