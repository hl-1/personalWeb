package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class JavaSourceStructureTest {

    private static final Path SERVER_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final List<Path> SOURCE_ROOTS = List.of(
            SERVER_ROOT.resolve("src/main/java"),
            SERVER_ROOT.resolve("src/test/java"));

    @Test
    void keepsOneTopLevelTypePerJavaSourceFile() throws IOException {
        List<Path> sources = javaSources();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "source structure verification requires a JDK");

        List<String> violations = new ArrayList<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    files,
                    null,
                    List.of("-proc:none"),
                    null,
                    files.getJavaFileObjectsFromPaths(sources));

            for (CompilationUnitTree unit : task.parse()) {
                long typeCount = unit.getTypeDecls().stream()
                        .filter(ClassTree.class::isInstance)
                        .count();
                if (typeCount > 1) {
                    Path source = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                    violations.add(relativePath(source) + " declares " + typeCount + " top-level types");
                }
            }
        }

        assertEquals(List.of(), violations.stream().sorted().toList(),
                () -> "Each Java source file must own at most one top-level type:\n"
                        + String.join("\n", violations));
    }

    private static List<Path> javaSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(sources::add);
            }
        }
        return sources.stream().sorted().toList();
    }

    private static String relativePath(Path source) {
        return SERVER_ROOT.relativize(source).toString().replace('\\', '/');
    }
}
