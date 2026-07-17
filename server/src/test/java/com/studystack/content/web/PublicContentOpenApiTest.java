package com.studystack.content.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.content.application.PublicArticleQuery;
import com.studystack.content.infrastructure.seo.ContentSitemapContributor;
import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.portfolio.infrastructure.seo.PortfolioSitemapContributor;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        PublicContentOpenApiTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
        "management.endpoint.health.validate-group-membership=false"
})
class PublicContentOpenApiTest {

    static final String DATABASE_AUTO_CONFIGURATION_EXCLUSIONS =
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration";

    @Autowired
    MockMvc mockMvc;

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
    void documentsP2PublicContentPathsWithoutSessionRequirements() throws Exception {
        JsonNode document = apiDocument();

        assertEquals("P2", document.path("info").path("version").asText());
        assertEquals(
                Set.of(
                        "/api/v1/articles",
                        "/api/v1/articles/{slug}",
                        "/api/v1/categories",
                        "/api/v1/tags"),
                contentPaths(document));
        for (String path : contentPaths(document)) {
            JsonNode operation = document.path("paths").path(path).path("get");
            assertFalse(operation.isMissingNode(), path);
            assertTrue(operation.path("security").isMissingNode() || operation.path("security").isEmpty(), path);
        }
    }

    @Test
    void documentsPaginationBoundsPageSchemaAndProblemResponses() throws Exception {
        JsonNode document = apiDocument();
        JsonNode listOperation = document.path("paths").path("/api/v1/articles").path("get");

        JsonNode page = parameter(listOperation, "page").path("schema");
        assertEquals(0, page.path("minimum").asInt());
        assertEquals(0, page.path("default").asInt());
        JsonNode size = parameter(listOperation, "size").path("schema");
        assertEquals(1, size.path("minimum").asInt());
        assertEquals(50, size.path("maximum").asInt());
        assertEquals(10, size.path("default").asInt());
        assertFalse(parameter(listOperation, "category").isMissingNode());
        assertFalse(parameter(listOperation, "tag").isMissingNode());

        JsonNode pageSchema = document.path("components").path("schemas").path("ArticlePageResponse");
        assertEquals(
                Set.of("items", "page", "size", "totalElements", "totalPages"),
                propertyNames(pageSchema));
        assertTrue(listOperation.path("responses").has("200"));
        assertTrue(listOperation.path("responses").has("400"));
        assertTrue(listOperation.path("responses").path("400").path("content")
                .has("application/problem+json"));

        JsonNode detailOperation = document.path("paths").path("/api/v1/articles/{slug}").path("get");
        assertTrue(detailOperation.path("responses").has("200"));
        assertTrue(detailOperation.path("responses").has("404"));
        assertTrue(detailOperation.path("responses").path("404").path("content")
                .has("application/problem+json"));
    }

    @Test
    void documentsRequiredNullableFieldsAndPublicProblemCode() throws Exception {
        JsonNode document = apiDocument();
        JsonNode schemas = document.path("components").path("schemas");
        JsonNode summary = schemas.path("ArticleSummaryResponse");
        assertEquals(propertyNames(summary), stringValues(summary.path("required")));
        assertNullable(summary.path("properties").path("category"));

        JsonNode detail = schemas.path("ArticleDetailResponse");
        assertEquals(propertyNames(detail), stringValues(detail.path("required")));
        assertNullable(detail.path("properties").path("seoTitle"));
        assertNullable(detail.path("properties").path("seoDescription"));

        JsonNode problem = schemas.path("PublicProblemResponse");
        assertEquals(Set.of("type", "title", "status", "detail", "instance", "code"),
                propertyNames(problem));
        assertEquals(propertyNames(problem), stringValues(problem.path("required")));
        assertEquals("#/components/schemas/PublicProblemResponse",
                document.path("paths").path("/api/v1/articles/{slug}").path("get")
                        .path("responses").path("404").path("content")
                        .path("application/problem+json").path("schema").path("$ref").asText());
    }

    private JsonNode apiDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private Set<String> contentPaths(JsonNode document) {
        Set<String> paths = new java.util.HashSet<>();
        document.path("paths").fieldNames().forEachRemaining(paths::add);
        return paths.stream()
                .filter(path -> path.startsWith("/api/v1/articles")
                        || path.equals("/api/v1/categories")
                        || path.equals("/api/v1/tags"))
                .collect(java.util.stream.Collectors.toSet());
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private Set<String> propertyNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.path("properties").fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private Set<String> stringValues(JsonNode node) {
        Set<String> values = new java.util.HashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return Set.copyOf(values);
    }

    private void assertNullable(JsonNode property) {
        assertTrue(property.path("nullable").asBoolean()
                || stringValues(property.path("type")).contains("null"));
    }
}
