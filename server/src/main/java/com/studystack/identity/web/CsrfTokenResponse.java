package com.studystack.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSRF token required for state-changing requests")
public record CsrfTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "X-CSRF-TOKEN")
        String headerName) {
}
