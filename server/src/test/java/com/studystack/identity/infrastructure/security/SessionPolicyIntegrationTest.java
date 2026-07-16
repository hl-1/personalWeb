package com.studystack.identity.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studystack.StudyStackApplication;
import com.studystack.identity.support.GitHubOAuthProviderStub;
import jakarta.servlet.http.Cookie;
import java.io.Serializable;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(SessionPolicyIntegrationTest.ProbeConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        })
class SessionPolicyIntegrationTest {

    private static final String SESSION_COOKIE = "STUDYSTACK_SESSION";
    private static final String CLIENT_SECRET = "EXAMPLE_ONLY_GITHUB_CLIENT_SECRET";
    private static final String PROBE_PATH = "/__test/session/principal";

    static final GitHubOAuthProviderStub GITHUB = GitHubOAuthProviderStub.start();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void githubProviderProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.client.provider.github.authorization-uri",
                GITHUB::authorizationUri);
        registry.add(
                "spring.security.oauth2.client.provider.github.token-uri",
                GITHUB::tokenUri);
        registry.add(
                "spring.security.oauth2.client.provider.github.user-info-uri",
                GITHUB::userInfoUri);
        registry.add(
                "spring.security.oauth2.client.provider.github.user-name-attribute",
                () -> "id");
    }

    @BeforeEach
    void resetState() {
        GITHUB.reset();
        jdbcTemplate.update("delete from spring_session_attributes");
        jdbcTemplate.update("delete from spring_session");
        jdbcTemplate.update("delete from identity_external_identity");
        jdbcTemplate.update("delete from identity_user_account");
    }

    @AfterAll
    static void stopProvider() {
        GITHUB.close();
    }

    @Test
    void persistsApprovedJdbcSessionPolicyWithoutSensitiveOAuthData() throws Exception {
        assertEquals("jdbc", environment.getProperty("spring.session.store-type"));
        assertEquals("never", environment.getProperty("spring.session.jdbc.initialize-schema"));
        assertEquals(
                "0 */10 * * * *",
                environment.getProperty("spring.session.jdbc.cleanup-cron"));

        OAuthResult result = completeLogin(port);

        assertNotNull(result.sessionBeforeLogin());
        assertNotNull(result.sessionAfterLogin());
        assertNotEquals(result.sessionBeforeLogin(), result.sessionAfterLogin());
        assertEquals(1, rowCount("spring_session"));
        assertEquals(
                86_400,
                jdbcTemplate.queryForObject(
                        "select max_inactive_interval from spring_session",
                        Integer.class));
        assertSensitiveValuesAbsent(sessionPayload());
        assertPrincipalShape();
    }

    @Test
    void expiredDatabaseSessionDoesNotRestoreAuthentication() throws Exception {
        OAuthResult result = completeLogin(port);
        assertEquals(HttpStatus.OK.value(), probe(port, result.sessionAfterLogin()).statusCode());

        jdbcTemplate.update(
                "update spring_session set last_access_time = ?, expiry_time = ?",
                0L,
                0L);

        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                probe(port, result.sessionAfterLogin()).statusCode());
    }

    @Test
    void defaultLogoutWithCsrfDeletesDatabaseSession() throws Exception {
        OAuthResult result = completeLogin(port);
        assertEquals(1, rowCount("spring_session"));

        mockMvc.perform(post("/logout")
                        .cookie(new Cookie(SESSION_COOKIE, result.sessionAfterLogin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(0, rowCount("spring_session"));
    }

    @Test
    void secondApplicationContextRestoresAuthenticationFromJdbcSession() throws Exception {
        OAuthResult result = completeLogin(port);
        HttpResponse<String> firstProbe = probe(port, result.sessionAfterLogin());
        assertEquals(HttpStatus.OK.value(), firstProbe.statusCode());

        try (ConfigurableApplicationContext context = startApplication("test")) {
            int restartedPort = ((ServletWebServerApplicationContext) context)
                    .getWebServer()
                    .getPort();
            HttpResponse<String> restartedProbe =
                    probe(restartedPort, result.sessionAfterLogin());

            assertEquals(HttpStatus.OK.value(), restartedProbe.statusCode());
            assertEquals(firstProbe.body(), restartedProbe.body());
        }
    }

    @Test
    void cookieFlagsMatchTestDevelopmentAndProductionProfiles() throws Exception {
        assertCookiePolicy(oauthEntry(port), false);

        try (ConfigurableApplicationContext development = startApplication("dev")) {
            int developmentPort = ((ServletWebServerApplicationContext) development)
                    .getWebServer()
                    .getPort();
            assertCookiePolicy(oauthEntry(developmentPort), false);
        }

        try (ConfigurableApplicationContext production = startApplication("prod")) {
            int productionPort = ((ServletWebServerApplicationContext) production)
                    .getWebServer()
                    .getPort();
            assertCookiePolicy(oauthEntry(productionPort), true);
        }
    }

    private OAuthResult completeLogin(int applicationPort) throws Exception {
        HttpResponse<String> entry = oauthEntry(applicationPort);
        assertEquals(HttpStatus.FOUND.value(), entry.statusCode());
        URI providerAuthorization = location(entry);
        String sessionBeforeLogin = responseCookie(entry);
        assertNotNull(sessionBeforeLogin);

        HttpResponse<String> authorization = get(providerAuthorization, null);
        assertEquals(HttpStatus.FOUND.value(), authorization.statusCode());
        URI callback = location(authorization);
        assertEquals(
                queryParameter(providerAuthorization, "state"),
                queryParameter(callback, "state"));

        HttpResponse<String> callbackResponse = get(callback, sessionBeforeLogin);
        assertEquals(HttpStatus.FOUND.value(), callbackResponse.statusCode());
        assertEquals("/login?status=success", location(callbackResponse).toString());
        return new OAuthResult(
                sessionBeforeLogin,
                responseCookie(callbackResponse));
    }

    private HttpResponse<String> oauthEntry(int applicationPort) throws Exception {
        return get(applicationUri(applicationPort, "/oauth2/authorization/github"), null);
    }

    private HttpResponse<String> probe(int applicationPort, String sessionCookie)
            throws Exception {
        return get(applicationUri(applicationPort, PROBE_PATH), sessionCookie);
    }

    private HttpResponse<String> get(URI uri, String sessionCookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
        if (sessionCookie != null) {
            request.header("Cookie", SESSION_COOKIE + "=" + sessionCookie);
        }
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI applicationUri(int applicationPort, String path) {
        return URI.create("http://localhost:" + applicationPort + path);
    }

    private URI location(HttpResponse<?> response) {
        return URI.create(response.headers().firstValue("Location").orElseThrow());
    }

    private String queryParameter(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
    }

    private String responseCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void assertCookiePolicy(HttpResponse<?> response, boolean secure) {
        String header = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow();
        List<String> directives = Arrays.stream(header.split(";"))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();

        assertTrue(directives.contains("path=/"));
        assertTrue(directives.contains("httponly"));
        assertTrue(directives.contains("samesite=lax"));
        assertEquals(secure, directives.contains("secure"));
    }

    private ConfigurableApplicationContext startApplication(String profile) {
        List<String> arguments = new java.util.ArrayList<>(List.of(
                "--server.port=0",
                "--spring.profiles.active=" + profile,
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF",
                "--springdoc.api-docs.enabled=false",
                "--springdoc.swagger-ui.enabled=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.security.oauth2.client.provider.github.authorization-uri="
                        + GITHUB.authorizationUri(),
                "--spring.security.oauth2.client.provider.github.token-uri="
                        + GITHUB.tokenUri(),
                "--spring.security.oauth2.client.provider.github.user-info-uri="
                        + GITHUB.userInfoUri(),
                "--spring.security.oauth2.client.provider.github.user-name-attribute=id"));
        if ("prod".equals(profile)) {
            arguments.add("--DB_PASSWORD=local-test-password");
            arguments.add("--GITHUB_CLIENT_ID=EXAMPLE_ONLY_PROD_GITHUB_CLIENT_ID");
            arguments.add("--GITHUB_CLIENT_SECRET=EXAMPLE_ONLY_PROD_GITHUB_CLIENT_SECRET");
            arguments.add("--STUDYSTACK_ADMIN_GITHUB_IDS=70002");
        }
        return new SpringApplicationBuilder(
                        StudyStackApplication.class,
                        ProbeConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .run(arguments.toArray(String[]::new));
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String sessionPayload() {
        List<byte[]> attributes = jdbcTemplate.query(
                "select attribute_bytes from spring_session_attributes",
                (resultSet, rowNumber) -> resultSet.getBytes(1));
        StringBuilder payload = new StringBuilder();
        for (byte[] attribute : attributes) {
            payload.append(new String(attribute, StandardCharsets.ISO_8859_1));
        }
        return payload.toString();
    }

    private void assertSensitiveValuesAbsent(String value) {
        assertFalse(value.contains(GitHubOAuthProviderStub.AUTHORIZATION_CODE));
        assertFalse(value.contains(GitHubOAuthProviderStub.ACCESS_TOKEN));
        assertFalse(value.contains(GitHubOAuthProviderStub.CLAIMS_MARKER));
        assertFalse(value.contains(CLIENT_SECRET));
    }

    private void assertPrincipalShape() {
        assertTrue(Serializable.class.isAssignableFrom(StudyStackPrincipal.class));
        assertEquals(
                List.of("userId", "providerSubject", "login", "displayName", "avatarUrl"),
                Arrays.stream(StudyStackPrincipal.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
    }

    private record OAuthResult(
            String sessionBeforeLogin,
            String sessionAfterLogin) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        SessionProbeController sessionProbeController() {
            return new SessionProbeController();
        }
    }

    @RestController
    static class SessionProbeController {

        @GetMapping(PROBE_PATH)
        ResponseEntity<Map<String, String>> principal(Authentication authentication) {
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof StudyStackPrincipal principal)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.ok(Map.of(
                    "userId", principal.userId().toString(),
                    "login", principal.login()));
        }
    }
}
