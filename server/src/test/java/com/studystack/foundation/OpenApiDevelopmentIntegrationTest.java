package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studystack.content.application.PublicArticleQuery;
import com.studystack.content.infrastructure.seo.ContentSitemapContributor;
import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.portfolio.infrastructure.seo.PortfolioSitemapContributor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                OpenApiDevelopmentIntegrationTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
                "management.endpoint.health.validate-group-membership=false"
        })
class OpenApiDevelopmentIntegrationTest {

    static final String DATABASE_AUTO_CONFIGURATION_EXCLUSIONS =
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration";

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PublicArticleQuery publicArticleQuery;

    @MockitoBean
    PublicPortfolioQuery publicPortfolioQuery;

    @MockitoBean
    ContentSitemapContributor contentSitemapContributor;

    @MockitoBean
    PortfolioSitemapContributor portfolioSitemapContributor;

    @Test
    void exposesOpenApiThreeAndSwaggerUiAndWritesCanonicalContract() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JsonNode document = objectMapper.readTree(response.getBody());
        writeCanonicalContract(document);

        assertTrue(document.path("openapi").asText().startsWith("3."));
        assertEquals("StudyStack API", document.path("info").path("title").asText());
        assertEquals("P2", document.path("info").path("version").asText());
        assertTrue(document.path("paths").isObject());

        ResponseEntity<String> swaggerUi =
                restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(HttpStatus.OK, swaggerUi.getStatusCode());
        assertNotNull(swaggerUi.getBody());
        assertTrue(swaggerUi.getBody().contains("Swagger UI"));
    }

    private void writeCanonicalContract(JsonNode document) throws Exception {
        Path output = Path.of("target", "openapi", "openapi.json");
        Files.createDirectories(output.getParent());
        String json = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(sortObjectKeys(normalizeGeneratedServer(document)))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        Files.writeString(output, json + "\n", StandardCharsets.UTF_8);
    }

    private JsonNode normalizeGeneratedServer(JsonNode document) {
        JsonNode normalized = document.deepCopy();
        JsonNode server = normalized.path("servers").path(0);
        if (server.isObject() && "Generated server url".equals(server.path("description").asText())) {
            ((ObjectNode) server).put("url", "/");
        }
        return normalized;
    }

    private JsonNode sortObjectKeys(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            ObjectNode sorted = objectMapper.createObjectNode();
            fields.forEach((name, value) -> sorted.set(name, sortObjectKeys(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sortObjectKeys(value)));
            return sorted;
        }
        return node;
    }
}
