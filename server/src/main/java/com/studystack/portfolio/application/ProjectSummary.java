package com.studystack.portfolio.application;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummary(
        UUID id,
        String slug,
        String title,
        String summary,
        boolean featured,
        Instant publishedAt,
        Instant updatedAt) {
}
