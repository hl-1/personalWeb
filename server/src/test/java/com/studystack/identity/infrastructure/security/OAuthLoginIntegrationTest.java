package com.studystack.identity.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

import com.studystack.identity.application.IdentityBindingService;
import com.studystack.identity.support.GitHubOAuthProviderStub;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false",
                "spring.session.jdbc.cleanup-cron=-"
        })
class OAuthLoginIntegrationTest {

    private static final String SESSION_COOKIE = "STUDYSTACK_SESSION";
    private static final String CLIENT_SECRET = "EXAMPLE_ONLY_GITHUB_CLIENT_SECRET";

    static final GitHubOAuthProviderStub GITHUB = GitHubOAuthProviderStub.start();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    IdentityBindingService bindingService;

    @MockitoSpyBean
    OAuthLoginFailureHandler failureHandler;

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
        reset(bindingService);
        doCallRealMethod().when(bindingService).bind(argThat(claims -> claims != null));
        clearInvocations(failureHandler);
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
    void completesLoginRotatesSessionAndRemovesOAuthSecrets(CapturedOutput output) throws Exception {
        OAuthResult result = completeLogin();

        assertEquals(302, result.callback().statusCode());
        assertEquals(
                "/login?status=success",
                location(result.callback()).toString(),
                this::lastFailureDescription);
        assertNotNull(result.sessionBeforeLogin());
        assertNotNull(result.sessionAfterLogin());
        assertNotEquals(result.sessionBeforeLogin(), result.sessionAfterLogin());
        assertEquals(1, rowCount("identity_user_account"));
        assertEquals(1, rowCount("identity_external_identity"));
        assertEquals(1, GITHUB.authorizationRequests());
        assertEquals(1, GITHUB.tokenRequests());
        assertEquals(1, GITHUB.userInfoRequests());
        assertSensitiveValuesAbsent(sessionPayload());
        assertSensitiveValuesAbsent(output.getAll());
    }

    @Test
    void mapsProviderDenialToFixedError() throws Exception {
        GITHUB.denyAuthorization();

        OAuthResult result = completeLogin();

        assertFixedError(result, "oauth_denied");
        assertEquals(0, rowCount("identity_user_account"));
        assertEquals(1, GITHUB.authorizationRequests());
        assertEquals(0, GITHUB.tokenRequests());
        assertEquals(0, GITHUB.userInfoRequests());
    }

    @Test
    void mapsInvalidClaimsToFixedError() throws Exception {
        GITHUB.profile("""
                {"id":false,"login":"octocat","name":"The Octocat",\
                "avatar_url":"https://avatars.example/octocat.png",\
                "sensitive_marker":"FULL_CLAIMS_MARKER_DO_NOT_STORE"}
                """);

        OAuthResult result = completeLogin();

        assertFixedError(result, "invalid_profile");
        assertEquals(0, rowCount("identity_user_account"));
    }

    @Test
    void mapsIdentityConflictToFixedError() throws Exception {
        GITHUB.profile(profile("40003"));
        doThrow(new DataIntegrityViolationException("fixed identity conflict"))
                .when(bindingService)
                .bind(argThat(claims -> "40003".equals(claims.providerSubject())));

        OAuthResult result = completeLogin();

        assertFixedError(result, "identity_conflict");
    }

    @Test
    void mapsDisabledAccountToFixedError() throws Exception {
        GITHUB.profile(profile("40004"));
        OAuthResult initialLogin = completeLogin();
        assertEquals("/login?status=success", location(initialLogin.callback()).toString());
        jdbcTemplate.update("update identity_user_account set status = 'DISABLED'");

        OAuthResult disabledLogin = completeLogin();

        assertFixedError(disabledLogin, "account_disabled");
    }

    @Test
    void mapsTokenAndUserInfoFailuresToGenericError() throws Exception {
        GITHUB.failTokenExchange();
        assertFixedError(completeLogin(), "login_failed");

        GITHUB.reset();
        GITHUB.failUserInfo();
        assertFixedError(completeLogin(), "login_failed");
    }

    private OAuthResult completeLogin() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> entry = get(
                client,
                applicationUri("/oauth2/authorization/github"),
                null);
        assertEquals(302, entry.statusCode());
        URI providerAuthorization = location(entry);
        assertEquals("127.0.0.1", providerAuthorization.getHost());
        String sessionBeforeLogin = responseCookie(entry);
        assertNotNull(sessionBeforeLogin);
        assertFalse(sessionBeforeLogin.isBlank());

        HttpResponse<String> authorization = get(client, providerAuthorization, null);
        assertEquals(302, authorization.statusCode());
        URI callbackUri = location(authorization);
        assertEquals("localhost", callbackUri.getHost());
        assertEquals(port, callbackUri.getPort());
        assertEquals(queryParameter(providerAuthorization, "state"), queryParameter(callbackUri, "state"));
        HttpResponse<String> callback = get(
                client,
                callbackUri,
                sessionBeforeLogin);

        return new OAuthResult(callback, sessionBeforeLogin, responseCookie(callback));
    }

    private HttpResponse<String> get(HttpClient client, URI uri, String sessionCookie)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
        if (sessionCookie != null) {
            request.header("Cookie", SESSION_COOKIE + "=" + sessionCookie);
        }
        return client.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI applicationUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private URI location(HttpResponse<?> response) {
        return URI.create(response.headers().firstValue("Location").orElseThrow());
    }

    private String queryParameter(URI uri, String name) {
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams();
        return query.getFirst(name);
    }

    private String responseCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void assertFixedError(OAuthResult result, String error) {
        assertEquals(302, result.callback().statusCode());
        URI redirect = location(result.callback());
        assertEquals(
                "/login?error=" + error,
                redirect.toString(),
                this::lastFailureDescription);
        assertFalse(redirect.toString().contains("code="));
        assertFalse(redirect.toString().contains("token"));
        assertFalse(redirect.toString().contains(GitHubOAuthProviderStub.CLAIMS_MARKER));
    }

    private void assertSensitiveValuesAbsent(String value) {
        assertFalse(value.contains(GitHubOAuthProviderStub.AUTHORIZATION_CODE));
        assertFalse(value.contains(GitHubOAuthProviderStub.ACCESS_TOKEN));
        assertFalse(value.contains(GitHubOAuthProviderStub.CLAIMS_MARKER));
        assertFalse(value.contains(CLIENT_SECRET));
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

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String profile(String subject) {
        return """
                {"id":%s,"login":"octocat","name":"The Octocat",\
                "avatar_url":"https://avatars.example/octocat.png",\
                "sensitive_marker":"FULL_CLAIMS_MARKER_DO_NOT_STORE"}
                """.formatted(subject);
    }

    private String lastFailureDescription() {
        return mockingDetails(failureHandler).getInvocations().stream()
                .filter(invocation -> "onAuthenticationFailure".equals(
                        invocation.getMethod().getName()))
                .reduce((first, second) -> second)
                .map(this::describeFailure)
                .orElse("No OAuth failure captured");
    }

    private String describeFailure(Invocation invocation) {
        AuthenticationException failure = invocation.getArgument(2);
        StringBuilder description = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (!description.isEmpty()) {
                description.append(" -> ");
            }
            description.append(current.getClass().getSimpleName());
            if (current instanceof OAuth2AuthenticationException oauthFailure) {
                description.append('[')
                        .append(oauthFailure.getError().getErrorCode())
                        .append(']');
            }
            current = current.getCause();
        }
        return description.toString();
    }

    private record OAuthResult(
            HttpResponse<String> callback,
            String sessionBeforeLogin,
            String sessionAfterLogin) {
    }
}
