package com.studystack.identity.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.identity.application.GitHubIdentityClaims;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SensitiveAuthenticationDataPolicyTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Pattern REAL_SECRET_SHAPE = Pattern.compile(
            "(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"
                    + "|AKIA[0-9A-Z]{16}|BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY)");
    private static final List<String> SERVER_ONLY_NAMES = List.of(
            "GITHUB_CLIENT_SECRET",
            "STUDYSTACK_ADMIN_GITHUB_IDS",
            "DB_PASSWORD");

    @Test
    void exampleConfigurationUsesExplicitNonProductionIdentityValues() throws IOException {
        Map<String, String> environment = readEnvironment(PROJECT_ROOT.resolve(".env.example"));

        assertEquals("EXAMPLE_ONLY_GITHUB_CLIENT_ID", environment.get("GITHUB_CLIENT_ID"));
        assertEquals("EXAMPLE_ONLY_GITHUB_CLIENT_SECRET", environment.get("GITHUB_CLIENT_SECRET"));
        assertEquals("", environment.get("STUDYSTACK_ADMIN_GITHUB_IDS"));
    }

    @Test
    void applicationSourcesContainNoRecognizedRealSecretShapes() throws IOException {
        String productionSources = readFiles(
                PROJECT_ROOT.resolve("server/src/main"), path -> true)
                + readFiles(
                        PROJECT_ROOT.resolve("web/src"),
                        path -> !path.getFileName().toString().endsWith(".spec.ts"));

        assertFalse(REAL_SECRET_SHAPE.matcher(productionSources).find());
    }

    @Test
    void securityValuesRemainRedactedWhenObjectsAreLogged(CapturedOutput output) {
        String subject = "987654321";
        String login = "sensitive-login";
        String displayName = "Sensitive Display Name";
        StudyStackPrincipal principal = new StudyStackPrincipal(
                UUID.fromString("2d65e30a-f450-4f8e-8ed9-5f36b2f7c322"),
                subject,
                login,
                displayName,
                null);
        GitHubIdentityClaims claims = new GitHubIdentityClaims(
                subject, login, displayName, "https://avatars.example/sensitive.png");

        System.out.println(principal);
        System.out.println(claims);

        String captured = output.getAll();
        assertTrue(captured.contains("StudyStackPrincipal[redacted]"));
        assertTrue(captured.contains("GitHubIdentityClaims[redacted]"));
        assertFalse(captured.contains(subject));
        assertFalse(captured.contains(login));
        assertFalse(captured.contains(displayName));
    }

    @Test
    void generatedFrontendAssetsContainNoServerAuthenticationConfiguration() throws IOException {
        Path distribution = PROJECT_ROOT.resolve("web/dist");
        if (!Files.isDirectory(distribution)) {
            return;
        }

        String assets = readFiles(distribution, path -> true);
        for (String serverOnlyName : SERVER_ONLY_NAMES) {
            assertFalse(assets.contains(serverOnlyName));
        }
        assertFalse(REAL_SECRET_SHAPE.matcher(assets).find());
    }

    private Map<String, String> readEnvironment(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts -> parts[1]));
    }

    private String readFiles(Path root, java.util.function.Predicate<Path> include)
            throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            StringBuilder content = new StringBuilder();
            for (Path path : paths.filter(Files::isRegularFile).filter(include).toList()) {
                content.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
            return content.toString();
        }
    }
}
