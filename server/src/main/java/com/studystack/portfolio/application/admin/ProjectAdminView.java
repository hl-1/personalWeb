package com.studystack.portfolio.application.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProjectAdminView(
        UUID id,
        String slug,
        String title,
        String summary,
        String descriptionMarkdown,
        String projectUrl,
        String repositoryUrl,
        Status status,
        boolean featured,
        int sortOrder,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public ProjectAdminView {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(status, "status is required");
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    public record Summary(
            UUID id,
            String slug,
            String title,
            String summary,
            Status status,
            boolean featured,
            int sortOrder,
            Instant publishedAt,
            Instant updatedAt,
            long version) {
    }
}
