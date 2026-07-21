package com.studystack.identity.web;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.content.application.PublicArticleQuery;
import com.studystack.content.infrastructure.seo.ContentSitemapContributor;
import com.studystack.identity.infrastructure.security.StudyStackPrincipal;
import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.portfolio.infrastructure.seo.PortfolioSitemapContributor;
import com.studystack.support.AdminNoDatabaseTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        AuthControllerIntegrationTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
        "management.endpoint.health.validate-group-membership=false"
})
class AuthControllerIntegrationTest extends AdminNoDatabaseTestSupport {

    static final String DATABASE_AUTO_CONFIGURATION_EXCLUSIONS =
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration";

    private static final String SESSION_COOKIE = "STUDYSTACK_SESSION";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String CSRF_PATH = "/api/v1/auth/csrf";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PublicArticleQuery publicArticleQuery;

    @MockitoBean
    PublicPortfolioQuery publicPortfolioQuery;

    @MockitoBean
    ContentSitemapContributor contentSitemapContributor;

    @MockitoBean
    PortfolioSitemapContributor portfolioSitemapContributor;

    @Test
    void anonymousCurrentUserReturnsDiscriminatedState() throws Exception {
        mockMvc.perform(get(ME_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").value(nullValue()));
    }

    @Test
    void authenticatedCurrentUserReturnsOnlyApprovedFieldsAndStableRoles() throws Exception {
        UUID userId = UUID.fromString("7f1f76df-c136-4696-bad6-c7c477d62a69");
        StudyStackPrincipal principal = new StudyStackPrincipal(
                userId,
                "40001",
                "octocat",
                "The Octocat",
                null);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_AUDITOR"),
                        new SimpleGrantedAuthority("ROLE_USER")),
                "github");

        mockMvc.perform(get(ME_PATH).with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.login").value("octocat"))
                .andExpect(jsonPath("$.user.displayName").value("The Octocat"))
                .andExpect(jsonPath("$.user.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"))
                .andExpect(jsonPath("$.user.roles[1]").value("ADMIN"))
                .andExpect(jsonPath("$.user.roles.length()").value(2))
                .andExpect(jsonPath("$.user.providerSubject").doesNotExist())
                .andExpect(jsonPath("$.user.token").doesNotExist())
                .andExpect(jsonPath("$.user.sessionId").doesNotExist());
    }

    @Test
    void csrfEndpointReturnsSessionBoundTokenAndHeaderName() throws Exception {
        CsrfExchange exchange = requestCsrf();

        assertNotNull(exchange.session());
        assertTrue(exchange.token().length() >= 16);
        assertTrue(exchange.session().getAttributeNames().hasMoreElements());
    }

    @Test
    void logoutRejectsMissingAndIncorrectCsrfTokens() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("csrf_failed"));

        CsrfExchange exchange = requestCsrf();
        mockMvc.perform(post(LOGOUT_PATH)
                        .session(exchange.session())
                        .header(exchange.headerName(), "incorrect-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("csrf_failed"));
    }

    @Test
    void logoutClearsSessionAndCookieAndIsIdempotent() throws Exception {
        CsrfExchange exchange = requestCsrf();
        OAuth2AuthenticationToken authentication = authenticatedUser();

        mockMvc.perform(post(LOGOUT_PATH)
                        .session(exchange.session())
                        .cookie(new Cookie(SESSION_COOKIE, "test-session-cookie"))
                        .header(exchange.headerName(), exchange.token())
                        .with(authentication(authentication)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SESSION_COOKIE, 0))
                .andExpect(content().string(""));
        assertTrue(exchange.session().isInvalid());

        CsrfExchange anonymousExchange = requestCsrf();
        mockMvc.perform(post(LOGOUT_PATH)
                        .session(anonymousExchange.session())
                        .header(anonymousExchange.headerName(), anonymousExchange.token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private CsrfExchange requestCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfExchange(
                response.path("token").asText(),
                response.path("headerName").asText(),
                (MockHttpSession) result.getRequest().getSession(false));
    }

    private OAuth2AuthenticationToken authenticatedUser() {
        StudyStackPrincipal principal = new StudyStackPrincipal(
                UUID.fromString("7f1f76df-c136-4696-bad6-c7c477d62a69"),
                "40001",
                "octocat",
                "The Octocat",
                "https://avatars.example/octocat.png");
        return new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github");
    }

    private record CsrfExchange(
            String token,
            String headerName,
            MockHttpSession session) {
    }
}
