package com.studystack.portfolio.web;

import com.studystack.portfolio.application.ProjectSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "Zero-based public project page")
public record ProjectPageResponse(
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<ProjectSummaryResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

    static ProjectPageResponse from(Page<ProjectSummary> result) {
        return new ProjectPageResponse(
                result.getContent().stream().map(ProjectSummaryResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
