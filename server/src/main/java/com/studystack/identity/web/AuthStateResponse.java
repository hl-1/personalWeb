package com.studystack.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current StudyStack authentication state")
public record AuthStateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean authenticated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        AuthUserResponse user) {
}
