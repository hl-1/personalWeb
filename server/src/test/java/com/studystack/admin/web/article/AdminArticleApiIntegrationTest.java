package com.studystack.admin.web.article;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.content.domain.Category;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.Tag;
import com.studystack.content.domain.TagRepository;
import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import com.studystack.identity.infrastructure.security.StudyStackPrincipal;
import com.studystack.shared.slug.SlugPolicy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
class AdminArticleApiIntegrationTest {

    private static final String ARTICLES_PATH = "/api/v1/admin/articles";
    private static final String SECRET_MARKDOWN = "# Private management body";

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

    @Autowired
    CategoryRepository categories;

    @Autowired
    TagRepository tags;

    @Autowired
    SlugPolicy slugPolicy;

    private OAuth2AuthenticationToken adminAuthentication;
    private OAuth2AuthenticationToken userAuthentication;
    private Category category;
    private Tag tag;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                truncate table admin_audit_log, content_article_tag, content_article,
                    content_category, content_tag, identity_external_identity,
                    identity_user_account cascade
                """);
        UserAccount admin = persistUser("admin", AccountStatus.ACTIVE);
        UserAccount user = persistUser("user", AccountStatus.ACTIVE);
        adminAuthentication = authenticationFor(admin, "70002");
        userAuthentication = authenticationFor(user, "70001");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        category = categories.save(new Category(
                UUID.randomUUID(), "Java", slugPolicy.create("java"), now));
        tag = tags.save(new Tag(
                UUID.randomUUID(), "Spring", slugPolicy.create("spring"), now));
    }

    @Test
    void protectsAdminRoutesAndRequiresCsrfForWrites() throws Exception {
        mockMvc.perform(get(ARTICLES_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ARTICLES_PATH).with(authentication(userAuthentication)))
                .andExpect(status().isForbidden());

        mockMvc.perform(admin(get(ARTICLES_PATH), false))
                .andExpect(status().isOk());

        mockMvc.perform(post(ARTICLES_PATH)
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleRequest("csrf-article"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(ARTICLES_PATH)
                        .with(authentication(adminAuthentication))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleRequest("csrf-article"))))
                .andExpect(status().isForbidden());

        assertEquals(0, auditCount());
    }

    @Test
    void createsReadsUpdatesAndDeletesWithOneAuditPerMutation() throws Exception {
        MvcResult created = mockMvc.perform(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleRequest("managed-article"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(ARTICLES_PATH + "/")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value("managed-article"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bodyMarkdown").value(SECRET_MARKDOWN))
                .andExpect(jsonPath("$.categoryId").value(category.id().toString()))
                .andExpect(jsonPath("$.tagIds[0]").value(tag.id().toString()))
                .andExpect(jsonPath("$.version").isNumber())
                .andReturn();
        Map<String, Object> createdBody = body(created);
        String id = (String) createdBody.get("id");
        long version = ((Number) createdBody.get("version")).longValue();
        assertAudit(1, "CREATE", id, version);

        mockMvc.perform(admin(get(ARTICLES_PATH + "/{id}", id), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(14)))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value(version))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.tags").doesNotExist());
        assertEquals(1, auditCount());

        Map<String, Object> update = articleRequest("managed-article-renamed");
        update.put("title", "Updated title");
        update.put("version", version);
        MvcResult updated = mockMvc.perform(admin(put(ARTICLES_PATH + "/{id}", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andReturn();
        long updatedVersion = ((Number) body(updated).get("version")).longValue();
        assertAudit(2, "UPDATE", id, updatedVersion);

        mockMvc.perform(admin(delete(ARTICLES_PATH + "/{id}", id)
                        .param("version", Long.toString(updatedVersion)), true))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertAudit(3, "DELETE", id, null);
    }

    @Test
    void publishesAndArchivesWithReturnedVersionsAndRejectsInvalidStates() throws Exception {
        Map<String, Object> created = createArticle("stateful-article");
        String id = (String) created.get("id");
        long draftVersion = ((Number) created.get("version")).longValue();

        MvcResult published = mockMvc.perform(admin(post(ARTICLES_PATH + "/{id}/publish", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", draftVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(draftVersion + 1))
                .andReturn();
        long publishedVersion = ((Number) body(published).get("version")).longValue();
        assertAudit(2, "PUBLISH", id, publishedVersion);

        expectProblem(admin(delete(ARTICLES_PATH + "/{id}", id)
                        .param("version", Long.toString(publishedVersion)), true),
                409, "draft_delete_only");
        assertEquals(2, auditCount());

        MvcResult archived = mockMvc.perform(admin(post(ARTICLES_PATH + "/{id}/archive", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", publishedVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.version").value(publishedVersion + 1))
                .andReturn();
        long archivedVersion = ((Number) body(archived).get("version")).longValue();
        assertAudit(3, "ARCHIVE", id, archivedVersion);

        expectProblem(admin(post(ARTICLES_PATH + "/{id}/publish", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", archivedVersion))),
                409, "invalid_state_transition");
        assertEquals(3, auditCount());
    }

    @Test
    void listsWithFixedPaginationFiltersAndNoAudit() throws Exception {
        createArticle("first-article");
        createArticle("second-article");
        int mutationAudits = auditCount();

        mockMvc.perform(admin(get(ARTICLES_PATH), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].*", hasSize(8)))
                .andExpect(jsonPath("$.items[0].bodyMarkdown").doesNotExist())
                .andExpect(jsonPath("$.items[0].version").isNumber());

        mockMvc.perform(admin(get(ARTICLES_PATH)
                        .param("page", "1")
                        .param("size", "1")
                        .param("status", "DRAFT")
                        .param("query", " ARTICLE "), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(admin(get(ARTICLES_PATH).param("size", "100"), false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        assertEquals(mutationAudits, auditCount());
    }

    @Test
    void rejectsInvalidParametersBodiesAndPublishTimesWithoutAudit() throws Exception {
        for (String path : List.of(
                ARTICLES_PATH + "?page=-1",
                ARTICLES_PATH + "?size=0",
                ARTICLES_PATH + "?size=101",
                ARTICLES_PATH + "?status=draft",
                ARTICLES_PATH + "/not-a-uuid")) {
            expectProblem(admin(get(path), false), 400, "validation_failed");
        }
        expectProblem(admin(get(ARTICLES_PATH).param("query", "x".repeat(101)), false),
                400, "validation_failed");

        Map<String, Object> invalidBody = articleRequest("invalid-body");
        invalidBody.put("title", " ");
        invalidBody.put("tagIds", List.of(tag.id(), tag.id()));
        MvcResult validation = expectProblem(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidBody)),
                400, "validation_failed");
        assertFalse(validation.getResponse().getContentAsString().contains(SECRET_MARKDOWN));
        assertTrue(body(validation).containsKey("fieldErrors"));

        Map<String, Object> shortSlug = articleRequest("valid-slug");
        shortSlug.put("slug", "ab");
        MvcResult shortSlugValidation = expectProblem(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(shortSlug)),
                400, "validation_failed");
        assertTrue(((Map<?, ?>) body(shortSlugValidation).get("fieldErrors")).containsKey("slug"));

        Map<String, Object> created = createArticle("invalid-time-article");
        String id = (String) created.get("id");
        long version = ((Number) created.get("version")).longValue();
        int auditsBeforeFailure = auditCount();
        expectProblem(admin(post(ARTICLES_PATH + "/{id}/publish", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + ",\"publishAt\":\"not-an-instant\"}"),
                400, "validation_failed");
        assertEquals(auditsBeforeFailure, auditCount());
    }

    @Test
    void returnsStableSanitizedNotFoundAndConflictProblemsWithoutAuditingFailures() throws Exception {
        Map<String, Object> created = createArticle("unique-article");
        String id = (String) created.get("id");
        long version = ((Number) created.get("version")).longValue();
        int auditsBeforeFailures = auditCount();

        MvcResult duplicate = expectProblem(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleRequest("unique-article"))),
                409, "duplicate_slug");
        assertFalse(duplicate.getResponse().getContentAsString().contains(SECRET_MARKDOWN));

        Map<String, Object> staleUpdate = articleRequest("unique-article");
        staleUpdate.put("version", version + 20);
        expectProblem(admin(put(ARTICLES_PATH + "/{id}", id), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(staleUpdate)),
                409, "stale_version");

        expectProblem(admin(get(ARTICLES_PATH + "/{id}", UUID.randomUUID()), false),
                404, "not_found");

        Map<String, Object> unknownTaxonomy = articleRequest("unknown-taxonomy");
        unknownTaxonomy.put("categoryId", UUID.randomUUID());
        expectProblem(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unknownTaxonomy)),
                404, "not_found");

        Map<String, Object> unknownTag = articleRequest("unknown-tag");
        unknownTag.put("tagIds", List.of(UUID.randomUUID()));
        expectProblem(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unknownTag)),
                404, "not_found");
        assertEquals(auditsBeforeFailures, auditCount());
    }

    private Map<String, Object> createArticle(String slug) throws Exception {
        MvcResult result = mockMvc.perform(admin(post(ARTICLES_PATH), true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleRequest(slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result);
    }

    private LinkedHashMap<String, Object> articleRequest(String slug) {
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("slug", slug);
        request.put("title", "Title " + slug);
        request.put("summary", "Summary " + slug);
        request.put("bodyMarkdown", SECRET_MARKDOWN);
        request.put("categoryId", category.id());
        request.put("tagIds", List.of(tag.id()));
        request.put("seoTitle", "SEO " + slug);
        request.put("seoDescription", "SEO description " + slug);
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
        assertEquals("ARTICLE", audit.get("resource_type"));
        assertEquals(UUID.fromString(resourceId), audit.get("resource_id"));
        assertEquals(resourceVersion, audit.get("resource_version"));
    }

}
