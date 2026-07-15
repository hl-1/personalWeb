package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.studystack.StudyStackApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class StudyStackModulesTest {

    private static final Set<String> EXPECTED_MODULES = Set.of(
            "admin",
            "comment",
            "content",
            "identity",
            "media",
            "portfolio",
            "shared");
    private static final Set<String> BUSINESS_MODULES = Set.of(
            "admin", "comment", "content", "identity", "media", "portfolio");
    private static final Path MAIN_PACKAGE = Path.of("src", "main", "java", "com", "studystack");

    @Test
    void discoversExactlyTheDeclaredApplicationModules() {
        ApplicationModules modules = ApplicationModules.of(StudyStackApplication.class);
        Set<String> actualModules = modules.stream()
                .map(ApplicationModule::getIdentifier)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(EXPECTED_MODULES), actualModules,
                "StudyStack must expose exactly the seven approved application modules");
    }

    @Test
    void verifiesDeclaredModuleDependencies() {
        ApplicationModules.of(StudyStackApplication.class).verify();
    }

    @Test
    void keepsBusinessModulesFreeOfImplementationTypes() throws IOException {
        for (String module : BUSINESS_MODULES) {
            assertEquals(Set.of("package-info.java"), javaFilesBelow(MAIN_PACKAGE.resolve(module)),
                    module + " must only declare its P0 module boundary");
        }
    }

    @Test
    void limitsSharedModuleToApprovedFoundationTypes() throws IOException {
        Set<String> allowedFiles = Set.of(
                "config/ProductionEnvironmentGuard.java",
                "package-info.java",
                "openapi/OpenApiConfiguration.java");
        Set<String> unexpectedFiles = new HashSet<>(javaFilesBelow(MAIN_PACKAGE.resolve("shared")));
        unexpectedFiles.removeAll(allowedFiles);

        assertEquals(Set.of(), unexpectedFiles,
                "shared contains Java files outside the approved P0 foundation types");
    }

    private static Set<String> javaFilesBelow(Path modulePath) throws IOException {
        try (var paths = Files.walk(modulePath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(modulePath::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }
}
