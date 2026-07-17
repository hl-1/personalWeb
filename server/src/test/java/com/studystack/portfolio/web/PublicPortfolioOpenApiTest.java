package com.studystack.portfolio.web;

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
import java.util.HashSet;
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
        PublicPortfolioOpenApiTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
        "management.endpoint.health.validate-group-membership=false"
})
class PublicPortfolioOpenApiTest {

    static final String DATABASE_AUTO_CONFIGURATION_EXCLUSIONS =
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PublicArticleQuery publicArticleQuery;
    @MockitoBean PublicPortfolioQuery publicPortfolioQuery;
    @MockitoBean ContentSitemapContributor contentSitemapContributor;
    @MockitoBean PortfolioSitemapContributor portfolioSitemapContributor;

    @Test
    void documentsAllAnonymousPortfolioGetOperationsAndResponses() throws Exception {
        JsonNode document = apiDocument();
        Set<String> expected = Set.of(
                "/api/v1/portfolio/profile",
                "/api/v1/portfolio/projects",
                "/api/v1/portfolio/projects/{slug}",
                "/api/v1/portfolio/skills",
                "/api/v1/portfolio/experiences");
        assertEquals(expected, portfolioPaths(document));

        for (String path : expected) {
            JsonNode operation = document.path("paths").path(path).path("get");
            assertFalse(operation.isMissingNode(), path);
            assertTrue(operation.path("responses").has("200"), path);
            assertTrue(operation.path("security").isMissingNode() || operation.path("security").isEmpty(), path);
        }
        assertTrue(document.path("paths").path("/api/v1/portfolio/profile")
                .path("get").path("responses").has("404"));
        assertTrue(document.path("paths").path("/api/v1/portfolio/projects/{slug}")
                .path("get").path("responses").has("404"));
    }

    @Test
    void documentsProjectPaginationBoundsAndExactResponseSchemas() throws Exception {
        JsonNode document = apiDocument();
        JsonNode operation = document.path("paths").path("/api/v1/portfolio/projects").path("get");
        JsonNode page = parameter(operation, "page").path("schema");
        JsonNode size = parameter(operation, "size").path("schema");
        assertEquals(0, page.path("minimum").asInt());
        assertEquals(0, page.path("default").asInt());
        assertEquals(1, size.path("minimum").asInt());
        assertEquals(50, size.path("maximum").asInt());
        assertEquals(10, size.path("default").asInt());
        assertFalse(parameter(operation, "featured").isMissingNode());
        assertTrue(operation.path("responses").has("400"));

        assertEquals(Set.of("items", "page", "size", "totalElements", "totalPages"),
                properties(document, "ProjectPageResponse"));
        assertEquals(Set.of("displayName", "headline", "bioHtml", "seoDescription"),
                properties(document, "PortfolioProfileResponse"));
        assertEquals(Set.of("id", "slug", "title", "summary", "featured", "publishedAt", "updatedAt"),
                properties(document, "ProjectSummaryResponse"));
        assertEquals(Set.of(
                        "id", "slug", "title", "summary", "featured", "publishedAt", "updatedAt",
                        "descriptionHtml", "projectUrl", "repositoryUrl", "canonicalPath"),
                properties(document, "ProjectDetailResponse"));
        assertEquals(Set.of("id", "name", "category", "summary"), properties(document, "SkillResponse"));
        assertEquals(Set.of("id", "organization", "role", "startDate", "endDate", "summaryHtml"),
                properties(document, "ExperienceResponse"));
    }

    @Test
    void documentsRequiredAndNullablePortfolioFields() throws Exception {
        JsonNode document = apiDocument();
        for (String schemaName : Set.of(
                "PortfolioProfileResponse", "ProjectSummaryResponse", "ProjectDetailResponse",
                "ProjectPageResponse", "SkillResponse", "ExperienceResponse")) {
            JsonNode schema = document.path("components").path("schemas").path(schemaName);
            assertEquals(properties(document, schemaName), stringValues(schema.path("required")), schemaName);
        }

        assertNullable(document, "PortfolioProfileResponse", "seoDescription");
        assertNullable(document, "ProjectDetailResponse", "projectUrl");
        assertNullable(document, "ProjectDetailResponse", "repositoryUrl");
        assertNullable(document, "SkillResponse", "summary");
        assertNullable(document, "ExperienceResponse", "endDate");
    }

    private JsonNode apiDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Set<String> portfolioPaths(JsonNode document) {
        Set<String> paths = new HashSet<>();
        document.path("paths").fieldNames().forEachRemaining(path -> {
            if (path.startsWith("/api/v1/portfolio/")) {
                paths.add(path);
            }
        });
        return Set.copyOf(paths);
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private Set<String> properties(JsonNode document, String schema) {
        Set<String> names = new HashSet<>();
        document.path("components").path("schemas").path(schema)
                .path("properties").fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private Set<String> stringValues(JsonNode node) {
        Set<String> values = new HashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return Set.copyOf(values);
    }

    private void assertNullable(JsonNode document, String schema, String property) {
        JsonNode definition = document.path("components").path("schemas").path(schema)
                .path("properties").path(property);
        assertTrue(definition.path("nullable").asBoolean()
                || stringValues(definition.path("type")).contains("null"), schema + "." + property);
    }
}
