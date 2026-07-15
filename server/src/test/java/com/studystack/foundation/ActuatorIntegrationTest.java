package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.hikari.connection-timeout=500",
                "spring.datasource.hikari.validation-timeout=250",
                "springdoc.api-docs.enabled=false",
                "springdoc.swagger-ui.enabled=false"
        })
class ActuatorIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @Order(1)
    void exposesHealthyLivenessReadinessAndInfo() {
        assertHealth("/actuator/health/liveness", HttpStatus.OK, "UP");
        assertHealth("/actuator/health/readiness", HttpStatus.OK, "UP");

        ResponseEntity<String> info = restTemplate.getForEntity("/actuator/info", String.class);
        assertEquals(HttpStatus.OK, info.getStatusCode());

        ResponseEntity<JsonNode> health = restTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertNotNull(health.getBody());
        assertFalse(health.getBody().has("components"), "anonymous health must hide component details");
    }

    @Test
    @Order(2)
    void doesNotExposeSensitiveManagementEndpoints() {
        for (String path : List.of(
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops",
                "/actuator/heapdump")) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), path);
        }
    }

    @Test
    @Order(3)
    void reportsDatabaseOutageThroughReadinessButNotLiveness() throws InterruptedException {
        POSTGRES.stop();

        ResponseEntity<JsonNode> readiness = awaitStatus(
                "/actuator/health/readiness",
                HttpStatus.SERVICE_UNAVAILABLE,
                Duration.ofSeconds(10));

        assertEquals("DOWN", readiness.getBody().path("status").asText());
        assertHealth("/actuator/health/liveness", HttpStatus.OK, "UP");
    }

    private ResponseEntity<JsonNode> awaitStatus(String path, HttpStatus expected, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        ResponseEntity<JsonNode> response;

        do {
            response = restTemplate.getForEntity(path, JsonNode.class);
            if (response.getStatusCode().equals(expected)) {
                return response;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);

        assertEquals(expected, response.getStatusCode(), path + " did not reach the expected status");
        return response;
    }

    private void assertHealth(String path, HttpStatus status, String healthStatus) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(path, JsonNode.class);
        assertEquals(status, response.getStatusCode(), path);
        assertNotNull(response.getBody(), path + " must return a body");
        assertEquals(healthStatus, response.getBody().path("status").asText(), path);
    }
}
