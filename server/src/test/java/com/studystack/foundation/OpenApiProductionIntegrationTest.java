package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("prod")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                OpenApiDevelopmentIntegrationTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
                "DB_PASSWORD=local-test-password",
                "GITHUB_CLIENT_ID=EXAMPLE_ONLY_GITHUB_CLIENT_ID",
                "GITHUB_CLIENT_SECRET=EXAMPLE_ONLY_GITHUB_CLIENT_SECRET",
                "STUDYSTACK_ADMIN_GITHUB_IDS=101",
                "STUDYSTACK_PUBLIC_BASE_URL=https://example.com",
                "management.endpoint.health.validate-group-membership=false"
        })
class OpenApiProductionIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @MockitoBean
    PublicArticleQuery publicArticleQuery;

    @MockitoBean
    PublicPortfolioQuery publicPortfolioQuery;

    @MockitoBean
    ContentSitemapContributor contentSitemapContributor;

    @MockitoBean
    PortfolioSitemapContributor portfolioSitemapContributor;

    @MockitoBean
    AdminAuditRepository adminAuditRepository;

    @MockitoBean
    ArticleAdminService articleAdminService;

    @MockitoBean
    TaxonomyAdminService taxonomyAdminService;

    @MockitoBean
    ProjectAdminService projectAdminService;

    @MockitoBean
    ProfileAdminService profileAdminService;

    @MockitoBean
    SkillAdminService skillAdminService;

    @MockitoBean
    ExperienceAdminService experienceAdminService;

    @Test
    void doesNotExposeOpenApiOrSwaggerUi() {
        for (String path : List.of(
                "/v3/api-docs",
                "/v3/api-docs/swagger-config",
                "/swagger-ui.html",
                "/swagger-ui/index.html")) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), path);
        }
    }
}
