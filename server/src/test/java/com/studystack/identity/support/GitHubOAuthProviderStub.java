package com.studystack.identity.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

public final class GitHubOAuthProviderStub implements AutoCloseable {

    public static final String AUTHORIZATION_CODE = "TEST_AUTHORIZATION_CODE_DO_NOT_LOG";
    public static final String ACCESS_TOKEN = "TEST_ACCESS_TOKEN_DO_NOT_STORE";
    public static final String CLAIMS_MARKER = "FULL_CLAIMS_MARKER_DO_NOT_STORE";

    private static final String DEFAULT_PROFILE = """
            {"id":40001,"login":"octocat","name":"The Octocat",\
            "avatar_url":"https://avatars.example/octocat.png",\
            "sensitive_marker":"FULL_CLAIMS_MARKER_DO_NOT_STORE"}
            """;

    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicReference<String> profileJson = new AtomicReference<>(DEFAULT_PROFILE);
    private final AtomicBoolean denyAuthorization = new AtomicBoolean();
    private final AtomicBoolean failToken = new AtomicBoolean();
    private final AtomicBoolean failUserInfo = new AtomicBoolean();
    private final AtomicInteger authorizationRequests = new AtomicInteger();
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final AtomicInteger userInfoRequests = new AtomicInteger();

    private GitHubOAuthProviderStub(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static GitHubOAuthProviderStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newCachedThreadPool();
            GitHubOAuthProviderStub stub = new GitHubOAuthProviderStub(server, executor);
            server.createContext("/authorize", stub::handleAuthorization);
            server.createContext("/token", stub::handleToken);
            server.createContext("/user", stub::handleUserInfo);
            server.setExecutor(executor);
            server.start();
            return stub;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start GitHub OAuth provider stub", exception);
        }
    }

    public String authorizationUri() {
        return baseUri() + "/authorize";
    }

    public String tokenUri() {
        return baseUri() + "/token";
    }

    public String userInfoUri() {
        return baseUri() + "/user";
    }

    public void profile(String json) {
        profileJson.set(Objects.requireNonNull(json, "json is required"));
    }

    public void denyAuthorization() {
        denyAuthorization.set(true);
    }

    public void failTokenExchange() {
        failToken.set(true);
    }

    public void failUserInfo() {
        failUserInfo.set(true);
    }

    public void reset() {
        profileJson.set(DEFAULT_PROFILE);
        denyAuthorization.set(false);
        failToken.set(false);
        failUserInfo.set(false);
        authorizationRequests.set(0);
        tokenRequests.set(0);
        userInfoRequests.set(0);
    }

    public int authorizationRequests() {
        return authorizationRequests.get();
    }

    public int tokenRequests() {
        return tokenRequests.get();
    }

    public int userInfoRequests() {
        return userInfoRequests.get();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handleAuthorization(HttpExchange exchange) throws IOException {
        authorizationRequests.incrementAndGet();
        MultiValueMap<String, String> query = UriComponentsBuilder
                .fromUri(exchange.getRequestURI())
                .build()
                .getQueryParams();
        String redirectUri = required(query.getFirst("redirect_uri"), "redirect_uri");
        String state = UriUtils.decode(
                required(query.getFirst("state"), "state"),
                StandardCharsets.UTF_8);
        UriComponentsBuilder callback = UriComponentsBuilder.fromUriString(redirectUri);
        if (denyAuthorization.get()) {
            callback.queryParam("error", "access_denied");
        } else {
            callback.queryParam("code", AUTHORIZATION_CODE);
        }
        callback.queryParam("state", state);
        redirect(exchange, callback.build().encode().toUri());
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        if (failToken.get()) {
            json(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        json(exchange, 200, """
                {"access_token":"TEST_ACCESS_TOKEN_DO_NOT_STORE",\
                "token_type":"bearer","scope":"read:user"}
                """);
    }

    private void handleUserInfo(HttpExchange exchange) throws IOException {
        userInfoRequests.incrementAndGet();
        if (failUserInfo.get()) {
            json(exchange, 500, "{\"error\":\"provider_unavailable\"}");
            return;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!("Bearer " + ACCESS_TOKEN).equals(authorization)) {
            json(exchange, 401, "{\"error\":\"invalid_token\"}");
            return;
        }
        json(exchange, 200, profileJson.get());
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private void redirect(HttpExchange exchange, URI location) throws IOException {
        exchange.getResponseHeaders().add("Location", location.toString());
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
