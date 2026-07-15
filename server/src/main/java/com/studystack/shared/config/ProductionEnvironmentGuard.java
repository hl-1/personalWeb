package com.studystack.shared.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

public final class ProductionEnvironmentGuard implements EnvironmentPostProcessor, Ordered {

    private static final String DATABASE_PASSWORD = "DB_PASSWORD";
    private static final String EXAMPLE_PREFIX = "EXAMPLE_ONLY_";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        String password = environment.getProperty(DATABASE_PASSWORD);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD is required when the prod profile is active");
        }
        if (password.startsWith(EXAMPLE_PREFIX)) {
            throw new IllegalStateException(
                    "DB_PASSWORD must not use an EXAMPLE_ONLY_ value when the prod profile is active");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
