package com.studystack.shared.seo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class PublicSiteProperties {

    private static final String PROPERTY_NAME = "STUDYSTACK_PUBLIC_BASE_URL";
    private static final String DEFAULT_DEVELOPMENT_URL = "http://localhost:5173";

    private final URI baseUrl;

    public PublicSiteProperties(
            @Value("${STUDYSTACK_PUBLIC_BASE_URL:http://localhost:5173}") String configuredBaseUrl,
            Environment environment) {
        this.baseUrl = normalize(configuredBaseUrl, isProduction(environment));
    }

    public URI baseUrl() {
        return baseUrl;
    }

    private static URI normalize(String configuredBaseUrl, boolean production) {
        String candidate = configuredBaseUrl == null
                ? DEFAULT_DEVELOPMENT_URL
                : configuredBaseUrl.trim();
        try {
            URI parsed = new URI(candidate);
            String scheme = parsed.getScheme() == null
                    ? null
                    : parsed.getScheme().toLowerCase(Locale.ROOT);
            String host = parsed.getHost() == null
                    ? null
                    : parsed.getHost().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()
                    || parsed.isOpaque()
                    || host == null
                    || parsed.getRawUserInfo() != null
                    || parsed.getRawQuery() != null
                    || parsed.getRawFragment() != null
                    || !hasNoPath(parsed)) {
                throw invalidBaseUrl();
            }
            if (production && !"https".equals(scheme)) {
                throw invalidBaseUrl();
            }
            if (!"https".equals(scheme)
                    && !("http".equals(scheme) && !production && isLoopbackHost(host))) {
                throw invalidBaseUrl();
            }
            int port = normalizeDefaultPort(scheme, parsed.getPort());
            return new URI(scheme, null, host, port, null, null, null);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalidBaseUrl();
        }
    }

    private static boolean hasNoPath(URI uri) {
        return uri.getRawPath() == null
                || uri.getRawPath().isEmpty()
                || "/".equals(uri.getRawPath());
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static int normalizeDefaultPort(String scheme, int port) {
        if (("https".equals(scheme) && port == 443)
                || ("http".equals(scheme) && port == 80)) {
            return -1;
        }
        return port;
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private static IllegalStateException invalidBaseUrl() {
        return new IllegalStateException(
                PROPERTY_NAME + " must be an origin without credentials, path, query, or fragment; "
                        + "production requires HTTPS and development HTTP is limited to localhost");
    }
}
