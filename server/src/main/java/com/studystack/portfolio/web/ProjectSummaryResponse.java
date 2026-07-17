package com.studystack.portfolio.web;

import com.studystack.portfolio.application.ProjectSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean featured,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant publishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt) {

    static ProjectSummaryResponse from(ProjectSummary summary) {
        return new ProjectSummaryResponse(
                summary.id(), summary.slug(), summary.title(), summary.summary(),
                summary.featured(), summary.publishedAt(), summary.updatedAt());
    }
}
