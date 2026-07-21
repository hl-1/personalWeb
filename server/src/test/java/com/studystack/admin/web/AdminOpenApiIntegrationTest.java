package com.studystack.admin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.admin.application.AdminArticleUseCase;
import com.studystack.admin.application.AdminMarkdownPreview;
import com.studystack.admin.application.AdminPortfolioUseCase;
import com.studystack.admin.application.AdminProjectUseCase;
import com.studystack.admin.application.AdminTaxonomyUseCase;
import com.studystack.admin.domain.AdminAuditRepository;
import com.studystack.content.application.PublicArticleQuery;
import com.studystack.content.application.admin.ArticleAdminService;
import com.studystack.content.application.admin.TaxonomyAdminService;
import com.studystack.content.infrastructure.seo.ContentSitemapContributor;
import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.portfolio.application.admin.ExperienceAdminService;
import com.studystack.portfolio.application.admin.ProfileAdminService;
import com.studystack.portfolio.application.admin.ProjectAdminService;
import com.studystack.portfolio.application.admin.SkillAdminService;
import com.studystack.portfolio.infrastructure.seo.PortfolioSitemapContributor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
                AdminOpenApiIntegrationTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
                "management.endpoint.health.validate-group-membership=false"
        })
class AdminOpenApiIntegrationTest {

    static final String DATABASE_AUTO_CONFIGURATION_EXCLUSIONS =
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration";

    private static final Map<String, Set<String>> EXPECTED_OPERATIONS = expectedOperations();
    private static final Set<String> CREATE_PATHS = Set.of(
            "/api/v1/admin/articles",
            "/api/v1/admin/categories",
            "/api/v1/admin/tags",
            "/api/v1/admin/portfolio/projects",
            "/api/v1/admin/portfolio/skills",
            "/api/v1/admin/portfolio/experiences");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean AdminArticleUseCase articleUseCase;
    @MockitoBean AdminTaxonomyUseCase taxonomyUseCase;
    @MockitoBean AdminProjectUseCase projectUseCase;
    @MockitoBean AdminPortfolioUseCase portfolioUseCase;
    @MockitoBean AdminMarkdownPreview markdownPreview;
    @MockitoBean AdminAuditRepository adminAuditRepository;
    @MockitoBean ArticleAdminService articleAdminService;
    @MockitoBean TaxonomyAdminService taxonomyAdminService;
    @MockitoBean ProjectAdminService projectAdminService;
    @MockitoBean ProfileAdminService profileAdminService;
    @MockitoBean SkillAdminService skillAdminService;
    @MockitoBean ExperienceAdminService experienceAdminService;
    @MockitoBean PublicArticleQuery publicArticleQuery;
    @MockitoBean PublicPortfolioQuery publicPortfolioQuery;
    @MockitoBean ContentSitemapContributor contentSitemapContributor;
    @MockitoBean PortfolioSitemapContributor portfolioSitemapContributor;

    @Test
    void publishesTheFixedAdminPathsSecurityCsrfAndResponseContract() throws Exception {
        JsonNode document = document();
        assertEquals("P3", document.at("/info/version").asText());
        assertSessionCookieScheme(document);

        for (Map.Entry<String, Set<String>> expected : EXPECTED_OPERATIONS.entrySet()) {
            for (String method : expected.getValue()) {
                JsonNode operation = operation(document, expected.getKey(), method);
                assertTrue(operation.isObject(), method + " " + expected.getKey());
                assertTrue(operation.path("security").toString().contains("sessionCookie"));
                assertResponse(operation, "400");
                assertResponse(operation, "401");
                assertResponse(operation, "403");
                assertProblemResponse(operation, "403");

                if (!"get".equals(method)) {
                    assertRequiredCsrfHeader(operation);
                }
                if ("delete".equals(method)) {
                    assertResponse(operation, "204");
                } else if ("post".equals(method) && CREATE_PATHS.contains(expected.getKey())) {
                    assertResponse(operation, "201");
                    assertResponse(operation, "409");
                } else {
                    assertResponse(operation, "200");
                }
                if (expected.getKey().contains("{id}")) {
                    assertResponse(operation, "404");
                    assertResponse(operation, "409");
                }
            }
        }
    }

    @Test
    void publishesPaginationAndVersionFieldsInAdminDtos() throws Exception {
        JsonNode document = document();
        assertResponseProperties(document, "/api/v1/admin/articles", "get",
                Set.of("items", "page", "size", "totalElements", "totalPages"));
        assertResponseProperties(document, "/api/v1/admin/portfolio/projects", "get",
                Set.of("items", "page", "size", "totalElements", "totalPages"));

        for (String path : Set.of(
                "/api/v1/admin/articles/{id}",
                "/api/v1/admin/categories",
                "/api/v1/admin/tags",
                "/api/v1/admin/portfolio/profile",
                "/api/v1/admin/portfolio/projects/{id}",
                "/api/v1/admin/portfolio/skills",
                "/api/v1/admin/portfolio/experiences")) {
            String method = path.contains("{id}") || path.endsWith("profile") ? "get" : "get";
            JsonNode schema = responseSchema(document, path, method, "200");
            if (schema.path("type").asText().equals("array")) {
                schema = schema.path("items");
            }
            assertTrue(resolveSchema(document, schema).path("properties").has("version"), path);
        }

        for (String path : Set.of(
                "/api/v1/admin/articles/{id}",
                "/api/v1/admin/categories/{id}",
                "/api/v1/admin/tags/{id}",
                "/api/v1/admin/portfolio/profile",
                "/api/v1/admin/portfolio/projects/{id}",
                "/api/v1/admin/portfolio/skills/{id}",
                "/api/v1/admin/portfolio/experiences/{id}")) {
            JsonNode requestSchema = operation(document, path, "put")
                    .path("requestBody").path("content").path("application/json").path("schema");
            assertTrue(resolveSchema(document, requestSchema).path("properties").has("version"), path);
        }
    }

    private JsonNode document() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return objectMapper.readTree(response.getBody());
    }

    private void assertSessionCookieScheme(JsonNode document) {
        JsonNode scheme = document.at("/components/securitySchemes/sessionCookie");
        assertEquals("apiKey", scheme.path("type").asText());
        assertEquals("cookie", scheme.path("in").asText());
        assertEquals("STUDYSTACK_SESSION", scheme.path("name").asText());
    }

    private void assertRequiredCsrfHeader(JsonNode operation) {
        boolean found = false;
        for (JsonNode parameter : operation.path("parameters")) {
            if ("X-CSRF-TOKEN".equals(parameter.path("name").asText())
                    && "header".equals(parameter.path("in").asText())
                    && parameter.path("required").asBoolean()) {
                found = true;
            }
        }
        assertTrue(found, operation.toString());
    }

    private void assertProblemResponse(JsonNode operation, String status) {
        assertTrue(operation.path("responses").path(status).path("content")
                .has("application/problem+json"));
    }

    private void assertResponse(JsonNode operation, String status) {
        assertTrue(operation.path("responses").has(status), status + " missing in " + operation);
    }

    private void assertResponseProperties(
            JsonNode document,
            String path,
            String method,
            Set<String> expected) {
        JsonNode properties = resolveSchema(document, responseSchema(document, path, method, "200"))
                .path("properties");
        for (String property : expected) {
            assertTrue(properties.has(property), path + " missing " + property);
        }
    }

    private JsonNode responseSchema(
            JsonNode document,
            String path,
            String method,
            String status) {
        return operation(document, path, method)
                .path("responses").path(status)
                .path("content").path("application/json").path("schema");
    }

    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        String reference = schema.path("$ref").asText();
        if (reference.isBlank()) {
            return schema;
        }
        return document.at(reference.substring(1));
    }

    private JsonNode operation(JsonNode document, String path, String method) {
        return document.path("paths").path(path).path(method);
    }

    private static Map<String, Set<String>> expectedOperations() {
        Map<String, Set<String>> operations = new LinkedHashMap<>();
        operations.put("/api/v1/admin/articles", Set.of("get", "post"));
        operations.put("/api/v1/admin/articles/{id}", Set.of("get", "put", "delete"));
        operations.put("/api/v1/admin/articles/{id}/publish", Set.of("post"));
        operations.put("/api/v1/admin/articles/{id}/archive", Set.of("post"));
        operations.put("/api/v1/admin/articles/preview", Set.of("post"));
        operations.put("/api/v1/admin/categories", Set.of("get", "post"));
        operations.put("/api/v1/admin/categories/{id}", Set.of("put", "delete"));
        operations.put("/api/v1/admin/tags", Set.of("get", "post"));
        operations.put("/api/v1/admin/tags/{id}", Set.of("put", "delete"));
        operations.put("/api/v1/admin/portfolio/profile", Set.of("get", "put"));
        operations.put("/api/v1/admin/portfolio/projects", Set.of("get", "post"));
        operations.put("/api/v1/admin/portfolio/projects/{id}", Set.of("get", "put", "delete"));
        operations.put("/api/v1/admin/portfolio/projects/{id}/publish", Set.of("post"));
        operations.put("/api/v1/admin/portfolio/projects/{id}/archive", Set.of("post"));
        operations.put("/api/v1/admin/portfolio/projects/preview", Set.of("post"));
        operations.put("/api/v1/admin/portfolio/skills", Set.of("get", "post"));
        operations.put("/api/v1/admin/portfolio/skills/{id}", Set.of("put", "delete"));
        operations.put("/api/v1/admin/portfolio/experiences", Set.of("get", "post"));
        operations.put("/api/v1/admin/portfolio/experiences/{id}", Set.of("put", "delete"));
        return Map.copyOf(operations);
    }
}
