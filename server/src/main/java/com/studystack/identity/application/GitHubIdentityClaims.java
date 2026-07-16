package com.studystack.identity.application;

import java.util.Objects;

public record GitHubIdentityClaims(
        String providerSubject,
        String login,
        String displayName,
        String avatarUrl) {

    public GitHubIdentityClaims {
        Objects.requireNonNull(providerSubject, "providerSubject is required");
        Objects.requireNonNull(login, "login is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    @Override
    public String toString() {
        return "GitHubIdentityClaims[redacted]";
    }
}
