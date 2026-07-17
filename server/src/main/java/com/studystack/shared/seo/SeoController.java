package com.studystack.shared.seo;

import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class SeoController {

    static final int MAXIMUM_URLS = 50_000;

    private static final MediaType XML_UTF_8 =
            new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);
    private static final MediaType TEXT_UTF_8 =
            new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
    private static final List<String> STATIC_PATHS = List.of("/", "/about", "/blog", "/projects");

    private final PublicSiteProperties properties;
    private final List<SitemapContributor> contributors;

    public SeoController(
            PublicSiteProperties properties,
            List<SitemapContributor> contributors) {
        this.properties = properties;
        this.contributors = List.copyOf(contributors);
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> sitemap() {
        Map<String, SitemapEntry> entries = new TreeMap<>();
        STATIC_PATHS.forEach(path -> entries.put(path, new SitemapEntry(path, null)));
        Instant now = Instant.now();
        contributors.stream()
                .flatMap(contributor -> contributor.entries(now).stream())
                .forEach(entry -> entries.merge(entry.path(), entry, SeoController::newerEntry));
        if (entries.size() > MAXIMUM_URLS) {
            throw new IllegalStateException(
                    "sitemap exceeds 50000 URLs; generate a sitemap index before increasing the limit");
        }

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        entries.values().forEach(entry -> appendEntry(xml, entry));
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(XML_UTF_8).body(xml.toString());
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> robots() {
        String body = """
                User-agent: *
                Disallow: /admin
                Disallow: /api
                Disallow: /oauth2
                Disallow: /login/oauth2
                Sitemap: %s/sitemap.xml
                """.formatted(properties.baseUrl());
        return ResponseEntity.ok().contentType(TEXT_UTF_8).body(body);
    }

    private void appendEntry(StringBuilder xml, SitemapEntry entry) {
        String location = properties.baseUrl().resolve(entry.path()).toString();
        xml.append("  <url>\n")
                .append("    <loc>").append(escapeXml(location)).append("</loc>\n");
        if (entry.lastModified() != null) {
            xml.append("    <lastmod>")
                    .append(entry.lastModified())
                    .append("</lastmod>\n");
        }
        xml.append("  </url>\n");
    }

    private static SitemapEntry newerEntry(SitemapEntry existing, SitemapEntry candidate) {
        if (existing.lastModified() == null) {
            return candidate;
        }
        if (candidate.lastModified() == null) {
            return existing;
        }
        return candidate.lastModified().isAfter(existing.lastModified()) ? candidate : existing;
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
