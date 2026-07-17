package com.studystack.identity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.content.application.PublicArticleQuery;
import com.studystack.content.infrastructure.seo.ContentSitemapContributor;
import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.portfolio.infrastructure.seo.PortfolioSitemapContributor;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings.Redirects;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                "management.endpoint.health.validate-group-membership=false"
        })
class IdentitySecurityBaselineTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ClientRegistrationRepository clientRegistrations;

    @Autowired
    SecurityFilterChain securityFilterChain;

    @MockitoBean
    PublicArticleQuery publicArticleQuery;

    @MockitoBean
    PublicPortfolioQuery publicPortfolioQuery;

    @MockitoBean
    ContentSitemapContributor contentSitemapContributor;

    @MockitoBean
    PortfolioSitemapContributor portfolioSitemapContributor;

    @Test
    void declaresApprovedSecurityDependencies() throws Exception {
        Map<String, String> scopes = declaredDependencyScopes();

        assertEquals("compile", scopes.get("org.springframework.boot:spring-boot-starter-security"));
        assertEquals("compile", scopes.get("org.springframework.boot:spring-boot-starter-oauth2-client"));
        assertEquals("compile", scopes.get("org.springframework.session:spring-session-jdbc"));
        assertEquals("test", scopes.get("org.springframework.security:spring-security-test"));
    }

    @Test
    void configuresOnlyTheApprovedGitHubScopeAndSessionPolicy() {
        ClientRegistration github = clientRegistrations.findByRegistrationId("github");

        assertNotNull(github);
        assertEquals("EXAMPLE_ONLY_GITHUB_CLIENT_ID", github.getClientId());
        assertEquals("EXAMPLE_ONLY_GITHUB_CLIENT_SECRET", github.getClientSecret());
        assertEquals(List.of("read:user"), github.getScopes().stream().sorted().toList());
    }

    @Test
    void keepsCsrfAndHeadersWhileEnablingOnlyOAuthLogin() {
        List<Object> filters = securityFilterChain.getFilters().stream()
                .map(Object.class::cast)
                .toList();

        assertTrue(containsFilter(filters, CsrfFilter.class));
        assertTrue(containsFilter(filters, HeaderWriterFilter.class));
        assertTrue(containsFilter(filters, AuthorizationFilter.class));
        assertTrue(containsFilter(filters, OAuth2AuthorizationRequestRedirectFilter.class));
        assertFalse(containsFilter(filters, UsernamePasswordAuthenticationFilter.class));
        assertFalse(containsFilter(filters, BasicAuthenticationFilter.class));
    }

    @Test
    void preservesAnonymousFoundationEndpointsAndBackendNotFoundBehavior() {
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity("/actuator/health/liveness", String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/not-implemented", String.class).getStatusCode());
        assertEquals(HttpStatus.FOUND,
                restTemplate
                        .withRedirects(Redirects.DONT_FOLLOW)
                        .getForEntity("/oauth2/authorization/github", String.class)
                        .getStatusCode());
    }

    private static boolean containsFilter(List<Object> filters, Class<?> filterType) {
        return filters.stream().anyMatch(filterType::isInstance);
    }

    private static Map<String, String> declaredDependencyScopes() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document pom = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        Map<String, String> scopes = new HashMap<>();

        NodeList dependencies = pom.getElementsByTagNameNS("*", "dependency");
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String scope = childText(dependency, "scope");
            scopes.put(groupId + ":" + artifactId, scope.isBlank() ? "compile" : scope);
        }
        return scopes;
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && (child.getLocalName() != null ? child.getLocalName() : child.getNodeName()).equals(name)) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
