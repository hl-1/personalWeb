package com.studystack.foundation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.StudyStackApplication;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ProductionEnvironmentPolicyTest {

    private static final String SENTINEL_PASSWORD = "EXAMPLE_ONLY_do_not_use";

    @Test
    void rejectsExamplePasswordWithoutEchoingIt() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> startProduction(SENTINEL_PASSWORD));

        String messages = failureMessages(failure);
        assertTrue(messages.contains("DB_PASSWORD"));
        assertFalse(messages.contains(SENTINEL_PASSWORD));
    }

    @Test
    void rejectsMissingProductionPassword() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> startProduction(null));

        assertTrue(failureMessages(failure).contains("DB_PASSWORD"));
    }

    @Test
    void acceptsNonExampleProductionPassword() {
        try (ConfigurableApplicationContext ignored = startProduction("local-test-password")) {
            assertTrue(ignored.isActive());
        }
    }

    private ConfigurableApplicationContext startProduction(String password) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("DB_HOST", "localhost");
        properties.put("DB_PORT", "5432");
        properties.put("DB_NAME", "studystack");
        properties.put("DB_USER", "studystack");
        if (password != null) {
            properties.put("DB_PASSWORD", password);
        }
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        properties.put("management.endpoint.health.validate-group-membership", "false");
        properties.put(
                "spring.autoconfigure.exclude",
                String.join(",",
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"));

        return new SpringApplicationBuilder(StudyStackApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .properties(properties)
                .run();
    }

    private String failureMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }
}
