package com.studystack.content.web;

import com.studystack.content.application.ArticleSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "Zero-based public article page")
public record ArticlePageResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<ArticleSummaryResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

    static ArticlePageResponse from(Page<ArticleSummary> result) {
        return new ArticlePageResponse(
                result.getContent().stream()
                        .map(ArticleSummaryResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
