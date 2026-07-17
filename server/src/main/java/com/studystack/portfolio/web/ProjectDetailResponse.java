package com.studystack.portfolio.web;

import com.studystack.portfolio.application.ProjectDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ProjectDetailResponse(
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
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String descriptionHtml,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String projectUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String repositoryUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String canonicalPath) {

    static ProjectDetailResponse from(ProjectDetail detail) {
        return new ProjectDetailResponse(
                detail.id(), detail.slug(), detail.title(), detail.summary(), detail.featured(),
                detail.publishedAt(), detail.updatedAt(), detail.descriptionHtml(), detail.projectUrl(),
                detail.repositoryUrl(), detail.canonicalPath());
    }
}
