package com.studystack.content.web;

import com.studystack.content.application.TaxonomySummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public taxonomy with its currently published article count")
public record TaxonomyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long publishedArticleCount) {

    static TaxonomyResponse from(TaxonomySummary taxonomy) {
        return new TaxonomyResponse(
                taxonomy.name(),
                taxonomy.slug(),
                taxonomy.publishedArticleCount());
    }
}
