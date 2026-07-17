package com.studystack.content.web;

import com.studystack.content.application.ArticleSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Public article summary")
public record ArticleSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String category,
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<String> tags,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant publishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt) {

    static ArticleSummaryResponse from(ArticleSummary article) {
        return new ArticleSummaryResponse(
                article.id(),
                article.slug(),
                article.title(),
                article.summary(),
                article.category(),
                article.tags(),
                article.publishedAt(),
                article.updatedAt());
    }
}
