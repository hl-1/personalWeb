package com.studystack.portfolio.web;

import com.studystack.portfolio.application.PortfolioProfileView;
import io.swagger.v3.oas.annotations.media.Schema;

public record PortfolioProfileResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String headline,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String bioHtml,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String seoDescription) {

    static PortfolioProfileResponse from(PortfolioProfileView view) {
        return new PortfolioProfileResponse(
                view.displayName(), view.headline(), view.bioHtml(), view.seoDescription());
    }
}
