package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P2ScopeContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Set<String> EXPECTED_RESPONSIBILITIES = Set.of("content", "portfolio", "shared");
    private static final Map<String, Set<String>> APPROVED_P2_JAVA_FILES = Map.of(
            "content", Set.of(
                    "server/src/main/java/com/studystack/content/application/ArticleDetail.java",
                    "server/src/main/java/com/studystack/content/application/ArticleNotFoundException.java",
                    "server/src/main/java/com/studystack/content/application/ArticleSummary.java",
                    "server/src/main/java/com/studystack/content/application/PublicArticleQuery.java",
                    "server/src/main/java/com/studystack/content/application/TaxonomySummary.java",
                    "server/src/main/java/com/studystack/content/domain/Article.java",
                    "server/src/main/java/com/studystack/content/domain/ArticleRepository.java",
                    "server/src/main/java/com/studystack/content/domain/ArticleStatus.java",
                    "server/src/main/java/com/studystack/content/domain/Category.java",
                    "server/src/main/java/com/studystack/content/domain/CategoryRepository.java",
                    "server/src/main/java/com/studystack/content/domain/ContentFieldRules.java",
                    "server/src/main/java/com/studystack/content/domain/ContentSlugConverter.java",
                    "server/src/main/java/com/studystack/content/domain/Tag.java",
                    "server/src/main/java/com/studystack/content/domain/TagRepository.java",
                    "server/src/main/java/com/studystack/content/infrastructure/seo/ContentSitemapContributor.java",
                    "server/src/main/java/com/studystack/content/web/ArticleController.java",
                    "server/src/main/java/com/studystack/content/web/ArticleDetailResponse.java",
                    "server/src/main/java/com/studystack/content/web/ArticlePageResponse.java",
                    "server/src/main/java/com/studystack/content/web/ArticleSummaryResponse.java",
                    "server/src/main/java/com/studystack/content/web/TaxonomyController.java",
                    "server/src/main/java/com/studystack/content/web/TaxonomyResponse.java"),
            "portfolio", Set.of(
                    "server/src/main/java/com/studystack/portfolio/application/ExperienceView.java",
                    "server/src/main/java/com/studystack/portfolio/application/PortfolioNotFoundException.java",
                    "server/src/main/java/com/studystack/portfolio/application/PortfolioProfileView.java",
                    "server/src/main/java/com/studystack/portfolio/application/ProjectDetail.java",
                    "server/src/main/java/com/studystack/portfolio/application/ProjectSummary.java",
                    "server/src/main/java/com/studystack/portfolio/application/PublicPortfolioQuery.java",
                    "server/src/main/java/com/studystack/portfolio/application/SkillView.java",
                    "server/src/main/java/com/studystack/portfolio/domain/Experience.java",
                    "server/src/main/java/com/studystack/portfolio/domain/ExperienceRepository.java",
                    "server/src/main/java/com/studystack/portfolio/domain/PortfolioProfile.java",
                    "server/src/main/java/com/studystack/portfolio/domain/PortfolioProfileRepository.java",
                    "server/src/main/java/com/studystack/portfolio/domain/PortfolioFieldRules.java",
                    "server/src/main/java/com/studystack/portfolio/domain/PortfolioSlugConverter.java",
                    "server/src/main/java/com/studystack/portfolio/domain/PortfolioUrlPolicy.java",
                    "server/src/main/java/com/studystack/portfolio/domain/Project.java",
                    "server/src/main/java/com/studystack/portfolio/domain/ProjectRepository.java",
                    "server/src/main/java/com/studystack/portfolio/domain/ProjectStatus.java",
                    "server/src/main/java/com/studystack/portfolio/domain/Skill.java",
                    "server/src/main/java/com/studystack/portfolio/domain/SkillRepository.java",
                    "server/src/main/java/com/studystack/portfolio/infrastructure/seo/PortfolioSitemapContributor.java",
                    "server/src/main/java/com/studystack/portfolio/web/ExperienceResponse.java",
                    "server/src/main/java/com/studystack/portfolio/web/PortfolioController.java",
                    "server/src/main/java/com/studystack/portfolio/web/PortfolioProfileResponse.java",
                    "server/src/main/java/com/studystack/portfolio/web/ProjectDetailResponse.java",
                    "server/src/main/java/com/studystack/portfolio/web/ProjectPageResponse.java",
                    "server/src/main/java/com/studystack/portfolio/web/ProjectSummaryResponse.java",
                    "server/src/main/java/com/studystack/portfolio/web/SkillResponse.java"),
            "shared", Set.of(
                    "server/src/main/java/com/studystack/shared/markdown/MarkdownRenderer.java",
                    "server/src/main/java/com/studystack/shared/markdown/RenderedMarkdown.java",
                    "server/src/main/java/com/studystack/shared/markdown/SafeMarkdownRenderer.java",
                    "server/src/main/java/com/studystack/shared/seo/PublicSiteProperties.java",
                    "server/src/main/java/com/studystack/shared/seo/SeoController.java",
                    "server/src/main/java/com/studystack/shared/seo/SitemapContributor.java",
                    "server/src/main/java/com/studystack/shared/seo/SitemapEntry.java",
                    "server/src/main/java/com/studystack/shared/slug/Slug.java",
                    "server/src/main/java/com/studystack/shared/slug/SlugPolicy.java",
                    "server/src/main/java/com/studystack/shared/web/PublicApiExceptionHandler.java",
                    "server/src/main/java/com/studystack/shared/web/PublicProblemResponse.java"));
    private static final Set<String> EXISTING_P2_MODULE_BOUNDARIES = Set.of(
            "server/src/main/java/com/studystack/content/package-info.java",
            "server/src/main/java/com/studystack/portfolio/package-info.java");
    private static final List<String> P2_IMPLEMENTATION_ROOTS = List.of(
            "server/src/main/java/com/studystack/content",
            "server/src/main/java/com/studystack/portfolio",
            "server/src/main/java/com/studystack/shared/slug",
            "server/src/main/java/com/studystack/shared/markdown",
            "server/src/main/java/com/studystack/shared/seo",
            "server/src/main/java/com/studystack/shared/web");
    private static final List<String> P2_CONTROLLER_ROOTS = List.of(
            "server/src/main/java/com/studystack/content",
            "server/src/main/java/com/studystack/portfolio");
    private static final List<String> LATER_PHASE_MODULES = List.of("admin", "comment", "media");
    private static final Pattern MAPPING_ANNOTATION =
            Pattern.compile("@(Post|Put|Patch|Delete)Mapping\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_METHOD =
            Pattern.compile("RequestMethod\\.(POST|PUT|PATCH|DELETE)\\b");
    private static final String P2_RESOURCE_NAME =
            "(?:article|blog|category|content|experience|portfolio|profile|project|skill|tag|taxonomy)";
    private static final Pattern ADMIN_CRUD_PAGE = Pattern.compile(
            "(?i)(?:admin.*" + P2_RESOURCE_NAME
                    + "|" + P2_RESOURCE_NAME + ".*(?:create|edit|manage|admin|form|crud)).*\\.vue$");

    @Test
    void declaresAnExactP2ImplementationResponsibilityMap() {
        assertEquals(EXPECTED_RESPONSIBILITIES, APPROVED_P2_JAVA_FILES.keySet(),
                "P2 exact responsibility map must be established before implementation");
        assertFalse(APPROVED_P2_JAVA_FILES.values().stream().anyMatch(Set::isEmpty),
                "each P2 responsibility must list exact approved Java files");
        assertTrue(approvedP2JavaFiles().stream()
                        .noneMatch(path -> path.contains("*") || path.endsWith("/")),
                "P2 responsibility map must list files, not directory or glob allowlists");
    }

    @Test
    void keepsP2ImplementationInsideApprovedResponsibilities() throws IOException {
        List<String> violations = outOfScopeP2Files(PROJECT_ROOT, approvedP2JavaFiles());

        assertTrue(violations.isEmpty(), () -> violationReport("P2 scope contains out-of-bounds files", violations));
    }

    @Test
    void keepsLaterPhaseModulesFreeOfImplementationTypes() throws IOException {
        List<String> violations = laterPhaseTypeViolations(PROJECT_ROOT);

        assertTrue(violations.isEmpty(), () -> violationReport("P2 introduced later-phase types", violations));
    }

    @Test
    void exposesNoP2WriteControllerMethods() throws IOException {
        List<String> violations = writeEndpointViolations(PROJECT_ROOT);

        assertTrue(violations.isEmpty(), () -> violationReport("P2 controllers must remain read-only", violations));
    }

    @Test
    void addsNoP2AdminCrudPages() throws IOException {
        List<String> violations = adminCrudPageViolations(PROJECT_ROOT);

        assertTrue(violations.isEmpty(), () -> violationReport("P2 must not add admin CRUD pages", violations));
    }

    @Test
    void reportsOutOfBoundsFilesAndHttpMethods(@TempDir Path temporaryProject) throws IOException {
        writeSource(
                temporaryProject,
                "server/src/main/java/com/studystack/content/web/DraftAdminController.java",
                "class DraftAdminController { @PatchMapping void update() {} }");
        writeSource(
                temporaryProject,
                "server/src/main/java/com/studystack/media/Upload.java",
                "class Upload {}");
        writeSource(
                temporaryProject,
                "web/src/views/ArticleEditView.vue",
                "<template><form /></template>");
        writeSource(
                temporaryProject,
                "web/src/views/SkillEditView.vue",
                "<template><form /></template>");

        List<String> violations = new ArrayList<>();
        violations.addAll(outOfScopeP2Files(temporaryProject, Set.of()));
        violations.addAll(laterPhaseTypeViolations(temporaryProject));
        violations.addAll(writeEndpointViolations(temporaryProject));
        violations.addAll(adminCrudPageViolations(temporaryProject));
        violations.sort(String::compareTo);
        String report = violationReport("P2 scope violations", violations);

        assertTrue(report.contains(
                "server/src/main/java/com/studystack/content/web/DraftAdminController.java: "
                        + "outside approved P2 responsibility map"));
        assertTrue(report.contains(
                "server/src/main/java/com/studystack/content/web/DraftAdminController.java: HTTP PATCH is forbidden"));
        assertTrue(report.contains(
                "server/src/main/java/com/studystack/media/Upload.java: media must remain package-info.java only"));
        assertTrue(report.contains("web/src/views/ArticleEditView.vue: P2 admin CRUD page is reserved for P3"));
        assertTrue(report.contains("web/src/views/SkillEditView.vue: P2 admin CRUD page is reserved for P3"));
    }

    private static Set<String> approvedP2JavaFiles() {
        return APPROVED_P2_JAVA_FILES.values().stream()
                .flatMap(Collection::stream)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private static List<String> outOfScopeP2Files(Path projectRoot, Set<String> approvedFiles)
            throws IOException {
        Set<String> allowedFiles = new TreeSet<>(approvedFiles);
        allowedFiles.addAll(EXISTING_P2_MODULE_BOUNDARIES);
        List<String> violations = new ArrayList<>();

        for (String root : P2_IMPLEMENTATION_ROOTS) {
            for (Path source : javaFilesBelow(projectRoot.resolve(root))) {
                String relative = relativePath(projectRoot, source);
                if (!allowedFiles.contains(relative)) {
                    violations.add(relative + ": outside approved P2 responsibility map");
                }
            }
        }
        return violations.stream().sorted().toList();
    }

    private static List<String> laterPhaseTypeViolations(Path projectRoot) throws IOException {
        List<String> violations = new ArrayList<>();
        for (String module : LATER_PHASE_MODULES) {
            Path moduleRoot = projectRoot.resolve(
                    "server/src/main/java/com/studystack/" + module);
            for (Path source : javaFilesBelow(moduleRoot)) {
                if (!source.getFileName().toString().equals("package-info.java")) {
                    violations.add(relativePath(projectRoot, source)
                            + ": " + module + " must remain package-info.java only");
                }
            }
        }
        return violations.stream().sorted().toList();
    }

    private static List<String> writeEndpointViolations(Path projectRoot) throws IOException {
        Set<String> violations = new TreeSet<>();
        for (String root : P2_CONTROLLER_ROOTS) {
            for (Path source : javaFilesBelow(projectRoot.resolve(root))) {
                String content = Files.readString(source);
                if (!source.getFileName().toString().endsWith("Controller.java")
                        && !content.contains("@Controller")
                        && !content.contains("@RestController")) {
                    continue;
                }
                String relative = relativePath(projectRoot, source);
                addMappingViolations(violations, relative, content, MAPPING_ANNOTATION);
                addMappingViolations(violations, relative, content, REQUEST_METHOD);
            }
        }
        return List.copyOf(violations);
    }

    private static void addMappingViolations(
            Set<String> violations,
            String relative,
            String content,
            Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String method = matcher.group(1).toUpperCase(java.util.Locale.ROOT);
            violations.add(relative + ": HTTP " + method + " is forbidden");
        }
    }

    private static List<String> adminCrudPageViolations(Path projectRoot) throws IOException {
        Path webSource = projectRoot.resolve("web/src");
        if (!Files.isDirectory(webSource)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(webSource)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> ADMIN_CRUD_PAGE.matcher(path.getFileName().toString()).matches())
                    .map(path -> relativePath(projectRoot, path)
                            + ": P2 admin CRUD page is reserved for P3")
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> javaFilesBelow(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
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
