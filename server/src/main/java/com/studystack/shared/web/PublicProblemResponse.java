package com.studystack.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import org.springframework.modulith.NamedInterface;

@NamedInterface("web")
@Schema(description = "Sanitized public API problem response")
public record PublicProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        URI type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        URI instance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code) {
}
