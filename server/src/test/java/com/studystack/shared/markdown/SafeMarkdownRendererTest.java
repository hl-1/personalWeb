package com.studystack.shared.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.stereotype.Component;

class SafeMarkdownRendererTest {

    private final MarkdownRenderer renderer = new SafeMarkdownRenderer();

    @Test
    void rendersApprovedCommonMarkAndGfmSyntax() {
        String markdown = """
                # Safe heading

                - first
                - second

                ```java
                System.out.println("safe");
                ```

                ~~obsolete~~

                | Name | Value |
                | --- | --- |
                | one | two |
                """;

        String html = renderer.render(markdown).html();

        assertTrue(html.contains("<h1>Safe heading</h1>"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>first</li>"));
        assertTrue(html.contains("<pre><code>"));
        assertTrue(html.contains("System.out.println"));
        assertTrue(html.contains("safe"));
        assertTrue(html.contains("<del>obsolete</del>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<thead>"));
        assertTrue(html.contains("<tbody>"));
    }

    @Test
    void keepsRootRelativeLinksWithoutExternalRel() {
        String html = renderer.render("[internal](/blog/safe-post)").html();

        assertTrue(html.contains("<a href=\"/blog/safe-post\">internal</a>"));
        assertFalse(html.contains(" rel="));
    }

    @Test
    void keepsPathRelativeLinksWithoutExternalRel() {
        String html = renderer.render("[internal](docs/safe-page)").html();

        assertTrue(html.contains("<a href=\"docs/safe-page\">internal</a>"));
        assertFalse(html.contains(" rel="));
    }

    @Test
    void securesExternalHttpsLinks() {
        String html = renderer.render("[external](https://example.com/docs)").html();

        assertTrue(html.contains("href=\"https://example.com/docs\""));
        assertTrue(html.contains("rel=\"nofollow noopener noreferrer\""));
    }

    @ParameterizedTest
    @MethodSource("unsafeLinks")
    void removesUnsafeLinkDestinations(String destination) {
        String html = renderer.render("[unsafe](" + destination + ")").html();
        String normalized = html.toLowerCase(Locale.ROOT);

        assertFalse(normalized.contains("href="));
        assertFalse(normalized.contains("javascript:"));
        assertFalse(normalized.contains("data:"));
        assertFalse(normalized.contains("http://"));
    }

    static Stream<String> unsafeLinks() {
        return Stream.of(
                "http://example.com",
                "javascript:alert(1)",
                "data:text/html;base64,PHNjcmlwdD4=",
                "//example.com/path");
    }

    @ParameterizedTest
    @MethodSource("maliciousMarkdown")
    void removesProhibitedHtmlAndAttributes(String markdown, List<String> forbiddenFragments) {
        String html = renderer.render(markdown).html().toLowerCase(Locale.ROOT);

        for (String fragment : forbiddenFragments) {
            assertFalse(html.contains(fragment), () -> "unexpected fragment: " + fragment + " in " + html);
        }
    }

    static Stream<Arguments> maliciousMarkdown() {
        return Stream.of(
                Arguments.of(
                        "<script>alert('xss')</script>",
                        List.of("<script", "</script")),
                Arguments.of(
                        "<strong onclick=\"alert(1)\">raw</strong>",
                        List.of("<strong", "onclick=")),
                Arguments.of(
                        "<style>body{display:none}</style>",
                        List.of("<style", "</style")),
                Arguments.of(
                        "<iframe src=\"https://example.com\"></iframe>",
                        List.of("<iframe", "src=")),
                Arguments.of(
                        "<object><embed src=\"https://example.com\"></object>",
                        List.of("<object", "<embed", "src=")),
                Arguments.of(
                        "<form><input onfocus=\"alert(1)\"></form>",
                        List.of("<form", "<input", "onfocus=")),
                Arguments.of(
                        "![alt](https://example.com/image.png)",
                        List.of("<img", "src=")),
                Arguments.of(
                        "<scr<script>ipt><img src=x onerror=alert(1)>",
                        List.of("<script", "<img", "onerror=")));
    }

    @Test
    void removesUnapprovedAttributesFromGeneratedHtml() {
        String html = renderer.render("[safe](https://example.com \"title\")").html();

        assertFalse(html.contains("title="));
        assertFalse(html.contains("style="));
        assertFalse(html.contains("class="));
    }

    @Test
    void returnsStableEmptyOutputForMissingContent() {
        assertEquals("", renderer.render(null).html());
        assertEquals("", renderer.render("").html());
        assertEquals("", renderer.render("   ").html());
    }

    @Test
    void rejectsMarkdownBeyondTheArticleBodyLimit() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render("a".repeat(200_001)));

        assertEquals("markdown must not exceed 200000 characters", failure.getMessage());
    }

    @Test
    void producesDeterministicOutputAndAnImmutableResult() {
        String markdown = "# Repeatable\n\n[safe](/about)";

        RenderedMarkdown first = renderer.render(markdown);
        RenderedMarkdown second = renderer.render(markdown);

        assertEquals(first.html(), second.html());
        assertTrue(Modifier.isFinal(RenderedMarkdown.class.getModifiers()));
        assertTrue(Arrays.stream(RenderedMarkdown.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        assertTrue(Arrays.stream(RenderedMarkdown.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertTrue(SafeMarkdownRenderer.class.isAnnotationPresent(Component.class));
    }

    @Test
    void configuresReusableThreadSafeRenderingComponentsOnce() throws ReflectiveOperationException {
        for (String fieldName : List.of("PARSER", "HTML_RENDERER", "HTML_POLICY")) {
            int modifiers = SafeMarkdownRenderer.class.getDeclaredField(fieldName).getModifiers();
            assertTrue(Modifier.isPrivate(modifiers));
            assertTrue(Modifier.isStatic(modifiers));
            assertTrue(Modifier.isFinal(modifiers));
        }

        String markdown = "# Concurrent\n\n[external](https://example.com)";
        Set<String> outputs = IntStream.range(0, 64)
                .parallel()
                .mapToObj(index -> renderer.render(markdown).html())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(1, outputs.size());
    }
}
