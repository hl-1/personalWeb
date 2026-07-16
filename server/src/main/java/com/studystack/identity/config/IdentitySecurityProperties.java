package com.studystack.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studystack.identity.security")
public record IdentitySecurityProperties(
        String adminGithubIds,
        Duration sessionTimeout,
        Cookie cookie) {

    public static final String ADMIN_GITHUB_IDS_ENVIRONMENT_VARIABLE =
            "STUDYSTACK_ADMIN_GITHUB_IDS";

    public record Cookie(
            String name,
            boolean httpOnly,
            String sameSite,
            String path,
            boolean secure) {
    }
}
