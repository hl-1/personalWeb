package com.studystack.shared.markdown;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.UrlSanitizer;
import org.owasp.html.AttributePolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
public final class SafeMarkdownRenderer implements MarkdownRenderer {

    private static final int MAX_MARKDOWN_LENGTH = 200_000;
    private static final String EXTERNAL_LINK_REL = "nofollow noopener noreferrer";
    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create());
    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(false)
            .sanitizeUrls(true)
            .urlSanitizer(new SafeUrlSanitizer())
            .attributeProviderFactory(context -> new SafeLinkAttributeProvider())
            .nodeRendererFactory(context -> new ProhibitedNodeRenderer())
            .build();
    private static final AttributePolicy SAFE_HREF =
            (elementName, attributeName, value) -> isAllowedLink(value) ? value : null;
    private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements(
                    "p", "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li", "blockquote", "pre", "code",
                    "em", "strong", "a", "hr", "br", "table", "thead",
                    "tbody", "tr", "th", "td", "del")
            .allowAttributes("href")
            .matching(SAFE_HREF)
            .onElements("a")
            .allowAttributes("rel")
            .matching(Pattern.compile("\\Anofollow noopener noreferrer\\z"))
            .onElements("a")
            .allowUrlProtocols("https")
            .toFactory();

    @Override
    public RenderedMarkdown render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new RenderedMarkdown("");
        }
        if (markdown.length() > MAX_MARKDOWN_LENGTH) {
            throw new IllegalArgumentException("markdown must not exceed 200000 characters");
        }

        Node document = PARSER.parse(markdown);
        String rendered = HTML_RENDERER.render(document);
        return new RenderedMarkdown(HTML_POLICY.sanitize(rendered));
    }

    private static boolean isAllowedLink(String value) {
        URI uri = parseSafeUri(value);
        if (uri == null) {
            return false;
        }
        if (isExternalHttps(uri)) {
            return true;
        }
        return !uri.isAbsolute()
                && uri.getRawAuthority() == null
                && uri.getRawPath() != null
                && !uri.getRawPath().isBlank();
    }

    private static boolean isExternalHttps(String value) {
        URI uri = parseSafeUri(value);
        return uri != null && isExternalHttps(uri);
    }

    private static boolean isExternalHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !uri.getHost().isBlank();
    }

    private static URI parseSafeUri(String value) {
        if (value == null
                || value.isBlank()
                || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static final class SafeUrlSanitizer implements UrlSanitizer {

        @Override
        public String sanitizeLinkUrl(String url) {
            return isAllowedLink(url) ? url : "";
        }

        @Override
        public String sanitizeImageUrl(String url) {
            return "";
        }
    }

    private static final class SafeLinkAttributeProvider implements AttributeProvider {

        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Link link && isExternalHttps(link.getDestination())) {
                attributes.put("rel", EXTERNAL_LINK_REL);
            }
        }
    }

    private static final class ProhibitedNodeRenderer implements NodeRenderer {

        private static final Set<Class<? extends Node>> PROHIBITED_NODE_TYPES =
                Set.of(HtmlBlock.class, HtmlInline.class, Image.class);

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return PROHIBITED_NODE_TYPES;
        }

        @Override
        public void render(Node node) {
            // Prohibited nodes intentionally produce no HTML.
        }
    }
}
