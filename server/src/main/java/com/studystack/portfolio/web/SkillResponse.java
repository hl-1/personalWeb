package com.studystack.portfolio.web;

import com.studystack.portfolio.application.SkillView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SkillResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String summary) {

    static SkillResponse from(SkillView view) {
        return new SkillResponse(view.id(), view.name(), view.category(), view.summary());
    }
}
