package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("prod")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                OpenApiDevelopmentIntegrationTest.DATABASE_AUTO_CONFIGURATION_EXCLUSIONS,
                "DB_PASSWORD=local-test-password",
                "management.endpoint.health.validate-group-membership=false"
        })
class OpenApiProductionIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

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
