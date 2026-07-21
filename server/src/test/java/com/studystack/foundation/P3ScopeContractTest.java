package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P3ScopeContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Set<String> APPROVED_ADMIN_DEPENDENCIES = Set.of(
            "content :: admin",
            "portfolio :: admin",
            "shared :: markdown",
            "shared :: web");
    private static final List<String> DEFERRED_MODULES = List.of("comment", "media");
    private static final Pattern ALLOWED_DEPENDENCIES = Pattern.compile(
            "allowedDependencies\\s*=\\s*\\{(?<values>[^}]*)}", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern BUSINESS_MODULE_REFERENCE = Pattern.compile(
            "com\\.studystack\\.(?:content|portfolio)\\.[A-Za-z0-9_.*]+(?:\\.[A-Za-z0-9_.*]+)*");
    private static final Pattern FORBIDDEN_P3_TYPE = Pattern.compile(
            "(?i)(?:comment|upload|rich.?text|bulk|revision).*(?:\\.java|\\.ts|\\.vue)$");
    private static final Pattern AUDIT_QUERY_TYPE = Pattern.compile(
            "(?i)audit.*(?:controller|query|response|view).*\\.java$");
    private static final Pattern AUDIT_QUERY_ROUTE = Pattern.compile(
            "(?i)/api/v1/admin/(?:audit|audits|audit-log|audit-logs)");

    @Test
    void declaresOnlyApprovedNamedInterfaceDependenciesForAdmin() throws IOException {
        Path moduleDescriptor = PROJECT_ROOT.resolve(
                "server/src/main/java/com/studystack/admin/package-info.java");

        assertEquals(APPROVED_ADMIN_DEPENDENCIES, declaredDependencies(Files.readString(moduleDescriptor)));
    }

    @Test
    void exposesContentAndPortfolioAdminPackagesAsNamedInterfaces() throws IOException {
        assertNamedAdminInterface("content");
        assertNamedAdminInterface("portfolio");
    }

    @Test
    void keepsDeferredModulesAndFeaturesOutsideP3() throws IOException {
        List<String> violations = deferredFeatureViolations(PROJECT_ROOT);

        assertTrue(violations.isEmpty(), () -> violationReport("P3 contains deferred features", violations));
    }

    @Test
    void keepsAdminAwayFromBusinessModuleInternals() throws IOException {
        List<String> violations = internalDependencyViolations(PROJECT_ROOT);

        assertTrue(violations.isEmpty(),
                () -> violationReport("P3 admin crosses a business module boundary", violations));
    }

    @Test
    void reportsTheSourceFileAndDependencyTarget(@TempDir Path temporaryProject) throws IOException {
        writeSource(
                temporaryProject,
                "server/src/main/java/com/studystack/admin/web/UnsafeController.java",
                "import com.studystack.content.domain.Article; "
                        + "class UnsafeController { com.studystack.portfolio.infrastructure.ProjectStore store; }");
        writeSource(
                temporaryProject,
                "server/src/main/java/com/studystack/comment/Comment.java",
                "class Comment {}");
        writeSource(
                temporaryProject,
                "server/src/main/java/com/studystack/admin/web/AuditQueryController.java",
                "class AuditQueryController {}");
        writeSource(
                temporaryProject,
                "web/src/features/admin/BulkEditor.ts",
                "export const bulkEditor = true;");

        List<String> violations = new ArrayList<>();
        violations.addAll(deferredFeatureViolations(temporaryProject));
        violations.addAll(internalDependencyViolations(temporaryProject));
        String report = violationReport("P3 scope violations", violations.stream().sorted().toList());

        assertTrue(report.contains(
                "server/src/main/java/com/studystack/admin/web/UnsafeController.java -> "
                        + "com.studystack.content.domain.Article"));
        assertTrue(report.contains(
                "server/src/main/java/com/studystack/admin/web/UnsafeController.java -> "
                        + "com.studystack.portfolio.infrastructure.ProjectStore"));
        assertTrue(report.contains(
                "server/src/main/java/com/studystack/comment/Comment.java: comment is reserved for P4"));
        assertTrue(report.contains(
                "server/src/main/java/com/studystack/admin/web/AuditQueryController.java: "
                        + "audit query API is outside P3"));
        assertTrue(report.contains(
                "web/src/features/admin/BulkEditor.ts: feature type is outside P3"));
    }

    private static void assertNamedAdminInterface(String module) throws IOException {
        Path descriptor = PROJECT_ROOT.resolve(
                "server/src/main/java/com/studystack/" + module + "/application/admin/package-info.java");

        assertTrue(Files.isRegularFile(descriptor), module + " must declare an application.admin package");
        assertTrue(Files.readString(descriptor).contains("@NamedInterface(\"admin\")"),
                module + " application.admin must be the named interface 'admin'");
    }

    private static Set<String> declaredDependencies(String descriptor) {
        Matcher declaration = ALLOWED_DEPENDENCIES.matcher(descriptor);
        assertTrue(declaration.find(), "admin must declare an explicit allowedDependencies array");
        Set<String> dependencies = new TreeSet<>();
        Matcher values = STRING_LITERAL.matcher(declaration.group("values"));
        while (values.find()) {
            dependencies.add(values.group(1));
        }
        return dependencies;
    }

    private static List<String> deferredFeatureViolations(Path projectRoot) throws IOException {
        List<String> violations = new ArrayList<>();
        for (String module : DEFERRED_MODULES) {
            Path moduleRoot = projectRoot.resolve("server/src/main/java/com/studystack/" + module);
            for (Path source : sourceFilesBelow(moduleRoot)) {
                if (!source.getFileName().toString().equals("package-info.java")) {
                    violations.add(relativePath(projectRoot, source) + ": " + module
                            + (module.equals("comment") ? " is reserved for P4" : " is reserved for P5"));
                }
            }
        }

        for (String root : List.of("server/src/main", "web/src")) {
            for (Path source : sourceFilesBelow(projectRoot.resolve(root))) {
                String relative = relativePath(projectRoot, source);
                String fileName = source.getFileName().toString();
                if (FORBIDDEN_P3_TYPE.matcher(fileName).find()
                        && !relative.endsWith("comment/package-info.java")) {
                    violations.add(relative + ": feature type is outside P3");
                }
                if (AUDIT_QUERY_TYPE.matcher(fileName).find()
                        || AUDIT_QUERY_ROUTE.matcher(Files.readString(source)).find()) {
                    violations.add(relative + ": audit query API is outside P3");
                }
            }
        }
        return violations.stream().distinct().sorted().toList();
    }

    private static List<String> internalDependencyViolations(Path projectRoot) throws IOException {
        Path adminRoot = projectRoot.resolve("server/src/main/java/com/studystack/admin");
        List<String> violations = new ArrayList<>();
        for (Path source : sourceFilesBelow(adminRoot)) {
            String content = Files.readString(source);
            Matcher references = BUSINESS_MODULE_REFERENCE.matcher(content);
            while (references.find()) {
                String target = references.group();
                if (!target.startsWith("com.studystack.content.application.admin.")
                        && !target.startsWith("com.studystack.portfolio.application.admin.")) {
                    violations.add(relativePath(projectRoot, source) + " -> " + target);
                }
            }
        }
        return violations.stream().distinct().sorted().toList();
    }

    private static List<Path> sourceFilesBelow(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(P3ScopeContractTest::isSourceFile)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".ts") || fileName.endsWith(".vue");
    }

    private static String relativePath(Path projectRoot, Path path) {
        return projectRoot.relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static String violationReport(String heading, List<String> violations) {
        return heading + ":" + System.lineSeparator() + String.join(System.lineSeparator(), violations);
    }

    private static void writeSource(Path projectRoot, String relative, String content) throws IOException {
        Path source = projectRoot.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }
}
