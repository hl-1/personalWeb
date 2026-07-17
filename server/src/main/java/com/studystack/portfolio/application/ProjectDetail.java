package com.studystack.portfolio.application;

import java.time.Instant;
import java.util.UUID;

public record ProjectDetail(
        UUID id,
        String slug,
        String title,
        String summary,
        boolean featured,
        Instant publishedAt,
        Instant updatedAt,
        String descriptionHtml,
        String projectUrl,
        String repositoryUrl,
        String canonicalPath) {
}
