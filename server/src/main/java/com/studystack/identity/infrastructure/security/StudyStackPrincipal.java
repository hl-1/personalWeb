package com.studystack.identity.infrastructure.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record StudyStackPrincipal(
        UUID userId,
        String providerSubject,
        String login,
        String displayName,
        String avatarUrl) implements OAuth2User, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    public StudyStackPrincipal {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(providerSubject, "providerSubject is required");
        Objects.requireNonNull(login, "login is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public String toString() {
        return "StudyStackPrincipal[redacted]";
    }
}
