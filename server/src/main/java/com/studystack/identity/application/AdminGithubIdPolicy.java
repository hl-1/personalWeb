package com.studystack.identity.application;

import com.studystack.identity.config.IdentitySecurityProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class AdminGithubIdPolicy {

    private static final List<String> USER_ROLES = List.of("USER");
    private static final List<String> ADMIN_ROLES = List.of("USER", "ADMIN");

    private final Set<String> adminGithubIds;

    public AdminGithubIdPolicy(
            IdentitySecurityProperties properties,
            Environment environment) {
        this.adminGithubIds = parse(
                properties.adminGithubIds(),
                environment.acceptsProfiles(Profiles.of("prod")));
    }

    public boolean isAdmin(String providerSubject) {
        return providerSubject != null && adminGithubIds.contains(providerSubject);
    }

    public List<String> rolesFor(String providerSubject) {
        return isAdmin(providerSubject) ? ADMIN_ROLES : USER_ROLES;
    }

    private Set<String> parse(String rawIds, boolean required) {
        if (rawIds == null || rawIds.isBlank()) {
            if (required) {
                throw invalidConfiguration();
            }
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String rawId : rawIds.split(",", -1)) {
            normalized.add(normalize(rawId.trim()));
        }
        return Set.copyOf(normalized);
    }

    private String normalize(String value) {
        if (value.isEmpty() || !isAsciiDigits(value)) {
            throw invalidConfiguration();
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw invalidConfiguration();
            }
            return Long.toString(id);
        } catch (NumberFormatException exception) {
            throw invalidConfiguration();
        }
    }

    private boolean isAsciiDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException(
                IdentitySecurityProperties.ADMIN_GITHUB_IDS_ENVIRONMENT_VARIABLE
                        + " must contain comma-separated positive ASCII integer IDs");
    }
}
