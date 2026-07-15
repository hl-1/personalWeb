package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class BuildRuntimeContractTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void runsOnJava21() {
        assertEquals(21, Runtime.version().feature(), "StudyStack must run on Java 21");
    }

    @Test
    void usesOneNonAggregatingMavenProject() throws Exception {
        List<Path> pomFiles;
        try (var paths = Files.walk(PROJECT_ROOT)) {
            pomFiles = paths
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.startsWith(PROJECT_ROOT.resolve("target")))
                    .toList();
        }

        assertEquals(List.of(PROJECT_ROOT.resolve("pom.xml")), pomFiles,
                "server must contain exactly one pom.xml");

        Document pom = readPom();
        assertEquals(0, pom.getElementsByTagNameNS("*", "modules").getLength(),
                "server/pom.xml must not declare Maven modules");
    }

    @Test
    void pinsDeclaredBuildVersions() throws Exception {
        Document pom = readPom();
        NodeList versionNodes = pom.getElementsByTagNameNS("*", "version");

        for (int index = 0; index < versionNodes.getLength(); index++) {
            Node versionNode = versionNodes.item(index);
            if (isEnforcerConfiguration(versionNode)) {
                continue;
            }
            assertPinned(versionNode.getTextContent().trim());
        }

        NodeList propertyNodes = pom.getElementsByTagNameNS("*", "properties").item(0).getChildNodes();
        for (int index = 0; index < propertyNodes.getLength(); index++) {
            Node property = propertyNodes.item(index);
            if (property.getNodeType() == Node.ELEMENT_NODE && property.getNodeName().endsWith(".version")) {
                assertPinned(property.getTextContent().trim());
            }
        }
    }

    @Test
    void providesAnnotatedSpringBootEntryPoint() throws Exception {
        Class<?> applicationClass;
        try {
            applicationClass = Class.forName("com.studystack.StudyStackApplication");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(
                    "com.studystack.StudyStackApplication must exist before the build contract can pass",
                    exception);
        }

        assertNotNull(applicationClass.getAnnotation(SpringBootApplication.class),
                "StudyStackApplication must use @SpringBootApplication");

        Method main = applicationClass.getDeclaredMethod("main", String[].class);
        assertTrue(Modifier.isPublic(main.getModifiers()), "main must be public");
        assertTrue(Modifier.isStatic(main.getModifiers()), "main must be static");
        assertEquals(void.class, main.getReturnType(), "main must return void");
    }

    private static Document readPom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(PROJECT_ROOT.resolve("pom.xml").toFile());
    }

    private static boolean isEnforcerConfiguration(Node node) {
        for (Node current = node.getParentNode(); current != null; current = current.getParentNode()) {
            if (current.getNodeType() == Node.ELEMENT_NODE && current.getNodeName().equals("configuration")) {
                return true;
            }
        }
        return false;
    }

    private static void assertPinned(String version) throws IOException {
        assertFalse(version.isBlank(), "declared versions must not be blank");
        assertFalse(version.equalsIgnoreCase("LATEST"), "LATEST is not a pinned version");
        assertFalse(version.equalsIgnoreCase("RELEASE"), "RELEASE is not a pinned version");
        assertFalse(version.toUpperCase().contains("SNAPSHOT"), "snapshot versions are forbidden: " + version);
        assertFalse(version.startsWith("[") || version.startsWith("("),
                "version ranges are forbidden: " + version);
    }
}
