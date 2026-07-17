package com.studystack.shared.seo;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studystack.content.domain.Article;
import com.studystack.content.domain.ArticleRepository;
import com.studystack.portfolio.domain.Project;
import com.studystack.portfolio.domain.ProjectRepository;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "STUDYSTACK_PUBLIC_BASE_URL=https://Example.COM/",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class SeoEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired PublicSiteProperties properties;
    @Autowired ArticleRepository articles;
    @Autowired ProjectRepository projects;
    @Autowired SlugPolicy slugPolicy;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPublicContent() {
        jdbcTemplate.execute("truncate table content_article_tag, content_article, content_category, "
                + "content_tag, portfolio_project cascade");
    }

    @Test
    void bindsAndNormalizesTheExactPublicBaseUrlConfiguration() {
        assertEquals("https://example.com", properties.baseUrl().toString());

        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        assertEquals(
                "http://localhost:5173",
                new PublicSiteProperties(" HTTP://LOCALHOST:5173/ ", test).baseUrl().toString());

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertEquals(
                "https://example.com",
                new PublicSiteProperties("https://EXAMPLE.COM:443/", prod).baseUrl().toString());

        for (String invalid : List.of(
                "http://localhost:5173",
                "https://example.com/path",
                "https://example.com?query=value",
                "https://example.com#fragment")) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new PublicSiteProperties(invalid, prod));
            assertTrue(failure.getMessage().contains("STUDYSTACK_PUBLIC_BASE_URL"));
        }
        assertThrows(
                IllegalStateException.class,
                () -> new PublicSiteProperties("http://example.com", test));
    }

    @Test
    void sitemapContainsOnlyCurrentPublicStaticAndModuleEntriesInStableOrder() throws Exception {
        Instant now = now();
        Article visibleArticle = article("public-article", now.minusSeconds(120));
        visibleArticle.publish(now.minusSeconds(90));
        Article draftArticle = article("draft-hidden", now.minusSeconds(60));
        Article futureArticle = article("future-hidden", now.minusSeconds(30));
        futureArticle.publish(now.plusSeconds(3_600));

        Project visibleProject = project("public-project", now.minusSeconds(120));
        visibleProject.publish(now.minusSeconds(80));
        Project draftProject = project("draft-hidden", now.minusSeconds(60));
        Project archivedProject = project("archived-hidden", now.minusSeconds(120));
        archivedProject.publish(now.minusSeconds(100));
        archivedProject.archive(now.minusSeconds(50));

        persist(() -> {
            List.of(visibleArticle, draftArticle, futureArticle).forEach(articles::save);
            List.of(visibleProject, draftProject, archivedProject).forEach(projects::save);
        });

        String xml = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn().getResponse().getContentAsString();

        assertEquals(List.of(
                        "https://example.com/",
                        "https://example.com/about",
                        "https://example.com/blog",
                        "https://example.com/blog/public-article",
                        "https://example.com/projects",
                        "https://example.com/projects/public-project"),
                locations(xml));
        assertTrue(xml.contains("<lastmod>" + visibleArticle.updatedAt() + "</lastmod>"));
        assertTrue(xml.contains("<lastmod>" + visibleProject.updatedAt() + "</lastmod>"));
        assertFalse(xml.contains("draft-hidden"));
        assertFalse(xml.contains("future-hidden"));
        assertFalse(xml.contains("archived-hidden"));
        assertFalse(xml.contains("/admin"));
        assertFalse(xml.contains("/api"));
        assertFalse(xml.contains("/oauth2"));
    }

    @Test
    void robotsDeclaresSitemapAndDisallowsSensitivePrefixes() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("User-agent: *")))
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /api")))
                .andExpect(content().string(containsString("Disallow: /oauth2")))
                .andExpect(content().string(containsString("Disallow: /login/oauth2")))
                .andExpect(content().string(containsString("Sitemap: https://example.com/sitemap.xml")));
    }

    @Test
    void sitemapEscapesXmlDeduplicatesPathsAndRejectsAnOversizedSingleDocument() {
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        PublicSiteProperties localProperties = new PublicSiteProperties("https://example.com", test);
        Instant newer = now();
        SitemapContributor duplicatesAndEscaping = ignored -> List.of(
                new SitemapEntry("/about", newer),
                new SitemapEntry("/research/a&b", newer));
        SeoController controller = new SeoController(localProperties, List.of(duplicatesAndEscaping));

        ResponseEntity<String> response = controller.sitemap();
        assertNotNull(response.getBody());
        assertEquals(1, occurrences(response.getBody(), "<loc>https://example.com/about</loc>"));
        assertTrue(response.getBody().contains("<loc>https://example.com/research/a&amp;b</loc>"));

        SitemapContributor oversized = ignored -> IntStream.range(0, 49_997)
                .mapToObj(index -> new SitemapEntry("/generated/" + index, null))
                .toList();
        SeoController oversizedController = new SeoController(localProperties, List.of(oversized));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, oversizedController::sitemap);
        assertTrue(failure.getMessage().contains("50000"));
        assertTrue(failure.getMessage().contains("sitemap index"));
    }

    private List<String> locations(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        NodeList nodes = document.getElementsByTagNameNS("*", "loc");
        List<String> locations = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            locations.add(nodes.item(index).getTextContent());
        }
        return locations;
    }

    private int occurrences(String value, String candidate) {
        return value.split(java.util.regex.Pattern.quote(candidate), -1).length - 1;
    }

    private Article article(String slug, Instant timestamp) {
        return new Article(
                UUID.randomUUID(), slugPolicy.create(slug), "Title " + slug, "Summary " + slug,
                "Body", null, null, null, timestamp);
    }

    private Project project(String slug, Instant timestamp) {
        return new Project(
                UUID.randomUUID(), slugPolicy.create(slug), "Title " + slug, "Summary " + slug,
                "Description", null, null, false, 0, timestamp);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void persist(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            action.run();
            entityManager.flush();
            entityManager.clear();
        });
    }
}
