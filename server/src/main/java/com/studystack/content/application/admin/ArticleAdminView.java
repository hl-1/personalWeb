package com.studystack.content.application.admin;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ArticleAdminView(
        UUID id,
        String slug,
        String title,
        String summary,
        String bodyMarkdown,
        Status status,
        UUID categoryId,
        List<UUID> tagIds,
        String seoTitle,
        String seoDescription,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public ArticleAdminView {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(status, "status is required");
        tagIds = List.copyOf(Objects.requireNonNull(tagIds, "tagIds is required"));
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
            Instant publishedAt,
            Instant updatedAt,
            long version) {
    }
}
