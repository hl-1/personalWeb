package com.studystack.admin.web;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import com.studystack.identity.infrastructure.security.StudyStackPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
class AdminAuthorizationIntegrationTest {

    private static final String ID = "00000000-0000-0000-0000-000000000099";
    private static final List<Route> ROUTES = List.of(
            get("/api/v1/admin/articles"), post("/api/v1/admin/articles"),
            get("/api/v1/admin/articles/" + ID), put("/api/v1/admin/articles/" + ID),
            delete("/api/v1/admin/articles/" + ID + "?version=0"),
            post("/api/v1/admin/articles/" + ID + "/publish"),
            post("/api/v1/admin/articles/" + ID + "/archive"),
            post("/api/v1/admin/articles/preview"),
            get("/api/v1/admin/categories"), post("/api/v1/admin/categories"),
            put("/api/v1/admin/categories/" + ID),
            delete("/api/v1/admin/categories/" + ID + "?version=0"),
            get("/api/v1/admin/tags"), post("/api/v1/admin/tags"),
            put("/api/v1/admin/tags/" + ID),
            delete("/api/v1/admin/tags/" + ID + "?version=0"),
            get("/api/v1/admin/portfolio/profile"), put("/api/v1/admin/portfolio/profile"),
            get("/api/v1/admin/portfolio/projects"), post("/api/v1/admin/portfolio/projects"),
            get("/api/v1/admin/portfolio/projects/" + ID),
            put("/api/v1/admin/portfolio/projects/" + ID),
            delete("/api/v1/admin/portfolio/projects/" + ID + "?version=0"),
            post("/api/v1/admin/portfolio/projects/" + ID + "/publish"),
            post("/api/v1/admin/portfolio/projects/" + ID + "/archive"),
            post("/api/v1/admin/portfolio/projects/preview"),
            get("/api/v1/admin/portfolio/skills"), post("/api/v1/admin/portfolio/skills"),
            put("/api/v1/admin/portfolio/skills/" + ID),
            delete("/api/v1/admin/portfolio/skills/" + ID + "?version=0"),
            get("/api/v1/admin/portfolio/experiences"), post("/api/v1/admin/portfolio/experiences"),
            put("/api/v1/admin/portfolio/experiences/" + ID),
            delete("/api/v1/admin/portfolio/experiences/" + ID + "?version=0"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserAccountRepository users;

    private OAuth2AuthenticationToken adminAuthentication;
    private OAuth2AuthenticationToken userAuthentication;

    @BeforeEach
    void resetUsers() {
        jdbcTemplate.execute("""
                truncate table admin_audit_log, identity_external_identity, identity_user_account cascade
                """);
        adminAuthentication = authenticationFor(persistUser("admin"), "70002");
        userAuthentication = authenticationFor(persistUser("user"), "70001");
    }

    @Test
    void protectsEveryFixedAdminMethodAndRequiresCsrfOnlyForWrites() throws Exception {
        for (Route route : ROUTES) {
            MockHttpServletRequestBuilder anonymous = route.request();
            if (route.write()) {
                anonymous.with(csrf());
            }
            expectProblem(anonymous, 401, "unauthorized");

            MockHttpServletRequestBuilder ordinaryUser = route.request()
                    .with(authentication(userAuthentication));
            if (route.write()) {
                ordinaryUser.with(csrf());
            }
            expectProblem(ordinaryUser, 403, "forbidden");

            MockHttpServletRequestBuilder administrator = route.request()
                    .with(authentication(adminAuthentication));
            if (route.write()) {
                administrator.with(csrf());
            }
            mockMvc.perform(administrator)
                    .andExpect(result -> assertFalse(
                            result.getResponse().getStatus() == 401
                                    || result.getResponse().getStatus() == 403,
                            route.method() + " " + route.path()));

            if (route.write()) {
                expectProblem(route.request().with(authentication(adminAuthentication)),
                        403, "csrf_failed");
                expectProblem(route.request()
                                .with(authentication(adminAuthentication))
                                .with(csrf().useInvalidToken()),
                        403, "csrf_failed");
            }
        }
    }

    private void expectProblem(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.type").value("urn:studystack:problem:" + expectedCode.replace('_', '-')))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").isNotEmpty())
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private OAuth2AuthenticationToken authenticationFor(UserAccount account, String providerSubject) {
        StudyStackPrincipal principal = new StudyStackPrincipal(
                account.id(), providerSubject, account.login(), account.displayName(), account.avatarUrl());
        return new OAuth2AuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "github");
    }

    private UserAccount persistUser(String prefix) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        return users.save(new UserAccount(
                id, prefix + "-" + id, prefix, null, AccountStatus.ACTIVE, now, now, now));
    }

    private static Route get(String path) {
        return new Route(HttpMethod.GET, path);
    }

    private static Route post(String path) {
        return new Route(HttpMethod.POST, path);
    }

    private static Route put(String path) {
        return new Route(HttpMethod.PUT, path);
    }

    private static Route delete(String path) {
        return new Route(HttpMethod.DELETE, path);
    }

    private record Route(HttpMethod method, String path) {

        boolean write() {
            return method != HttpMethod.GET;
        }

        MockHttpServletRequestBuilder request() {
            MockHttpServletRequestBuilder request =
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, path);
            if (method == HttpMethod.POST || method == HttpMethod.PUT) {
                request.contentType(MediaType.APPLICATION_JSON).content("{}");
            }
            return request;
        }
    }
}
