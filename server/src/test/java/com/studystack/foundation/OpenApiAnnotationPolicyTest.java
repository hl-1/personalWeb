package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OpenApiAnnotationPolicyTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Pattern LEGACY_PACKAGE =
            Pattern.compile("\\bimport\\s+io\\.swagger\\.annotations(?:\\.|;)");
    private static final Pattern LEGACY_ANNOTATION =
            Pattern.compile("@(Api|ApiOperation|ApiParam|ApiModel|ApiModelProperty)\\b");

    @Test
    void mainSourcesUseOnlyOpenApiThreeAnnotations() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var sources = Files.walk(MAIN_JAVA)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String content = Files.readString(source);
                if (LEGACY_PACKAGE.matcher(content).find()) {
                    violations.add(relative(source) + ": legacy io.swagger.annotations import");
                }
                if (LEGACY_ANNOTATION.matcher(content).find()) {
                    violations.add(relative(source) + ": legacy Swagger annotation");
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    private String relative(Path source) {
        return Path.of("").toAbsolutePath().normalize().relativize(source.toAbsolutePath().normalize()).toString();
    }
}
