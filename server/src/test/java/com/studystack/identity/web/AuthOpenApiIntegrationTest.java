package com.studystack.identity.web;

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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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
        AuthControllerIntegrationTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
        "management.endpoint.health.validate-group-membership=false"
})
class AuthOpenApiIntegrationTest {

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
    void documentsAuthenticationPathsResponsesSchemasAndSessionCookieInP2() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode document = objectMapper.readTree(body);

        assertEquals("P2", document.at("/info/version").asText());
        assertResponse(document, "/api/v1/auth/me", "get", "200");
        assertResponse(document, "/api/v1/auth/csrf", "get", "200");
        assertResponse(document, "/api/v1/auth/logout", "post", "204");
        assertResponse(document, "/api/v1/auth/logout", "post", "403");

        JsonNode securityScheme = document.at("/components/securitySchemes/sessionCookie");
        assertEquals("apiKey", securityScheme.path("type").asText());
        assertEquals("cookie", securityScheme.path("in").asText());
        assertEquals("STUDYSTACK_SESSION", securityScheme.path("name").asText());

        JsonNode authState = document.at("/components/schemas/AuthStateResponse/properties");
        assertTrue(authState.has("authenticated"));
        assertTrue(authState.has("user"));

        JsonNode authUser = document.at("/components/schemas/AuthUserResponse/properties");
        assertEquals(
                Set.of("id", "login", "displayName", "avatarUrl", "roles"),
                propertyNames(authUser));
        assertFalse(authUser.has("providerSubject"));
        assertFalse(authUser.has("token"));
        assertEquals(
                Set.of("USER", "ADMIN"),
                propertyNames(authUser.path("roles").path("items").path("enum")));

        JsonNode csrf = document.at("/components/schemas/CsrfTokenResponse/properties");
        assertEquals(Set.of("token", "headerName"), propertyNames(csrf));
    }

    private void assertResponse(
            JsonNode document,
            String path,
            String method,
            String status) {
        JsonNode response = document.path("paths")
                .path(path)
                .path(method)
                .path("responses")
                .path(status);
        assertFalse(response.isMissingNode(), path + " " + method + " " + status);
    }

    private Set<String> propertyNames(JsonNode objectOrArray) {
        if (objectOrArray.isArray()) {
            return StreamSupport.stream(objectOrArray.spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toSet());
        }
        return StreamSupport.stream(
                        ((Iterable<String>) objectOrArray::fieldNames).spliterator(),
                        false)
                .collect(Collectors.toSet());
    }
}
