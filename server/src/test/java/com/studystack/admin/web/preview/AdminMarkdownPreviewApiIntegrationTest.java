package com.studystack.admin.web.preview;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
class AdminMarkdownPreviewApiIntegrationTest {

    private static final String ARTICLE_PATH = "/api/v1/admin/articles/preview";
    private static final String PROJECT_PATH = "/api/v1/admin/portfolio/projects/preview";

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
                truncate table admin_audit_log,
                    content_article, content_category, content_tag,
                    portfolio_profile, portfolio_project, portfolio_skill, portfolio_experience,
                    identity_external_identity, identity_user_account cascade
                """);
        UserAccount admin = persistUser("admin", AccountStatus.ACTIVE);
        UserAccount user = persistUser("user", AccountStatus.ACTIVE);
        adminAuthentication = authenticationFor(admin, "70002");
        userAuthentication = authenticationFor(user, "70001");
    }

    @Test
    void protectsBothPreviewRoutesAndRequiresCsrf() throws Exception {
        for (String path : List.of(ARTICLE_PATH, PROJECT_PATH)) {
            mockMvc.perform(post(path)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request("# Preview")))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(path)
                            .with(authentication(userAuthentication))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request("# Preview")))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post(ARTICLE_PATH)
                        .with(authentication(adminAuthentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("# Preview")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PROJECT_PATH)
                        .with(authentication(adminAuthentication))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("# Preview")))
                .andExpect(status().isForbidden());
        assertNoSideEffects();
    }

    @Test
    void previewsEmptyAndMaximumLengthMarkdownWithoutSideEffects() throws Exception {
        for (String path : List.of(ARTICLE_PATH, PROJECT_PATH)) {
            mockMvc.perform(admin(post(path))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request("")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.*", hasSize(1)))
                    .andExpect(jsonPath("$.html").value(""))
                    .andExpect(jsonPath("$.markdown").doesNotExist());
        }

        mockMvc.perform(admin(post(ARTICLE_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("a".repeat(200_000))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.html").isString());
        mockMvc.perform(admin(post(PROJECT_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("p".repeat(100_000))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.html").isString());
        assertNoSideEffects();
    }

    @Test
    void rejectsMissingAndOverLengthMarkdownWithSafeProblems() throws Exception {
        for (Map.Entry<String, String> request : Map.of(
                ARTICLE_PATH, "a".repeat(200_001),
                PROJECT_PATH, "p".repeat(100_001)).entrySet()) {
            MvcResult result = mockMvc.perform(admin(post(request.getKey()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request(request.getValue())))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("validation_failed"))
                    .andReturn();
            assertFalse(result.getResponse().getContentAsString().contains(request.getValue()));
        }

        mockMvc.perform(admin(post(ARTICLE_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertNoSideEffects();
    }

    @Test
    void usesTheP2SanitizerForExecutableHtmlImagesAndUnsafeLinks() throws Exception {
        String markdown = """
                # Safe heading
                <script>alert('xss')</script>
                <strong onclick="alert(1)">raw</strong>
                [javascript](javascript:alert(1))
                [data](data:text/html;base64,PHNjcmlwdD4=)
                [http](http://example.com)
                ![image](https://example.com/image.png)
                <img src=x onerror=alert(1)>
                [safe](https://example.com/docs)
                """;

        MvcResult result = mockMvc.perform(admin(post(ARTICLE_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(markdown)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andReturn();
        String html = (String) body(result).get("html");
        String normalized = html.toLowerCase(Locale.ROOT);

        assertTrue(html.contains("<h1>Safe heading</h1>"));
        assertTrue(html.contains("href=\"https://example.com/docs\""));
        assertTrue(html.contains("rel=\"nofollow noopener noreferrer\""));
        for (String forbidden : List.of(
                "<script", "onclick=", "javascript:", "data:", "http://",
                "<img", "src=", "onerror=", markdown.toLowerCase(Locale.ROOT))) {
            assertFalse(normalized.contains(forbidden), () -> "unexpected fragment: " + forbidden);
        }
        assertNoSideEffects();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(authentication(adminAuthentication)).with(csrf());
    }

    private String request(String markdown) throws Exception {
        return objectMapper.writeValueAsString(Map.of("markdown", markdown));
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                new TypeReference<>() {
                });
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

    private void assertNoSideEffects() {
        assertEquals(0L, jdbcTemplate.queryForObject("""
                select
                    (select count(*) from admin_audit_log)
                    + (select count(*) from content_article)
                    + (select count(*) from content_category)
                    + (select count(*) from content_tag)
                    + (select count(*) from portfolio_profile)
                    + (select count(*) from portfolio_project)
                    + (select count(*) from portfolio_skill)
                    + (select count(*) from portfolio_experience)
                """, Long.class));
    }
}
