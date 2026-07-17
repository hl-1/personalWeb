package com.studystack.portfolio.web;

import com.studystack.portfolio.application.ExperienceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record ExperienceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String organization,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        LocalDate endDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String summaryHtml) {

    static ExperienceResponse from(ExperienceView view) {
        return new ExperienceResponse(
                view.id(), view.organization(), view.role(), view.startDate(), view.endDate(), view.summaryHtml());
    }
}
