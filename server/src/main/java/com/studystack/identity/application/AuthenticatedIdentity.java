package com.studystack.identity.application;

import java.util.Objects;
import java.util.UUID;

public record AuthenticatedIdentity(
        UUID userId,
        String providerSubject,
        String login,
        String displayName,
        String avatarUrl) {

    public AuthenticatedIdentity {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(providerSubject, "providerSubject is required");
        Objects.requireNonNull(login, "login is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    @Override
    public String toString() {
        return "AuthenticatedIdentity[redacted]";
    }
}
