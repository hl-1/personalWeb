package com.studystack.identity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

class IdentitySecurityPropertiesTest {

    private static final String OAUTH_PREFIX =
            "spring.security.oauth2.client.registration.github.";

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void developmentProfilesBindExampleOAuthAndNonSecureCookie(String profile) {
        try (ConfigurableApplicationContext context = start(profile)) {
            IdentitySecurityProperties properties =
                    context.getBean(IdentitySecurityProperties.class);
            Environment environment = context.getEnvironment();

            assertEquals("", properties.adminGithubIds());
            assertEquals(Duration.ofHours(24), properties.sessionTimeout());
            assertCookiePolicy(properties.cookie(), false);
            assertEquals("EXAMPLE_ONLY_GITHUB_CLIENT_ID",
                    environment.getProperty(OAUTH_PREFIX + "client-id"));
            assertEquals("EXAMPLE_ONLY_GITHUB_CLIENT_SECRET",
                    environment.getProperty(OAUTH_PREFIX + "client-secret"));
            assertEquals("read:user", environment.getProperty(OAUTH_PREFIX + "scope"));
            assertEquals("never", environment.getProperty("spring.session.jdbc.initialize-schema"));
        }
    }

    @Test
    void productionProfileBindsOnlyInjectedOAuthAndAdminValues() {
        try (ConfigurableApplicationContext context = start(
                "prod",
                "--DB_PASSWORD=local-test-password",
                "--GITHUB_CLIENT_ID=github-client-id-from-environment",
                "--GITHUB_CLIENT_SECRET=github-client-secret-from-environment",
                "--STUDYSTACK_ADMIN_GITHUB_IDS=101,202")) {
            IdentitySecurityProperties properties =
                    context.getBean(IdentitySecurityProperties.class);
            Environment environment = context.getEnvironment();

            assertEquals("101,202", properties.adminGithubIds());
            assertEquals(Duration.ofHours(24), properties.sessionTimeout());
            assertCookiePolicy(properties.cookie(), true);
            assertEquals("github-client-id-from-environment",
                    environment.getProperty(OAUTH_PREFIX + "client-id"));
            assertEquals("github-client-secret-from-environment",
                    environment.getProperty(OAUTH_PREFIX + "client-secret"));
            assertEquals("read:user", environment.getProperty(OAUTH_PREFIX + "scope"));
        }
    }

    @Test
    void productionConfigurationDoesNotContainOAuthOrAdminDefaults() throws Exception {
        String productionYaml = Files.readString(
                Path.of("src", "main", "resources", "application-prod.yml"));

        assertTrue(productionYaml.contains("${GITHUB_CLIENT_ID}"));
        assertTrue(productionYaml.contains("${GITHUB_CLIENT_SECRET}"));
        assertTrue(productionYaml.contains("${STUDYSTACK_ADMIN_GITHUB_IDS}"));
        assertFalse(productionYaml.contains("EXAMPLE_ONLY_"));
    }

    private static ConfigurableApplicationContext start(String profile, String... additionalArguments) {
        List<String> arguments = new ArrayList<>(List.of(
                "--spring.profiles.active=" + profile,
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF"));
        arguments.addAll(Arrays.asList(additionalArguments));

        return new SpringApplicationBuilder(IdentityConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(arguments.toArray(String[]::new));
    }

    private static void assertCookiePolicy(
            IdentitySecurityProperties.Cookie cookie,
            boolean secure) {
        assertEquals("STUDYSTACK_SESSION", cookie.name());
        assertTrue(cookie.httpOnly());
        assertEquals("Lax", cookie.sameSite());
        assertEquals("/", cookie.path());
        assertEquals(secure, cookie.secure());
    }
}
