package com.studystack.content.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ArticleDetail(
        UUID id,
        String slug,
        String title,
        String summary,
        String category,
        List<String> tags,
        Instant publishedAt,
        Instant updatedAt,
        String contentHtml,
        String seoTitle,
        String seoDescription,
        String canonicalPath) {

    public ArticleDetail {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(slug, "slug is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(summary, "summary is required");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags are required"));
        Objects.requireNonNull(publishedAt, "publishedAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        Objects.requireNonNull(contentHtml, "contentHtml is required");
        Objects.requireNonNull(canonicalPath, "canonicalPath is required");
    }
}
