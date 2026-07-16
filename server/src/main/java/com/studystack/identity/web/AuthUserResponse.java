package com.studystack.identity.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Authenticated StudyStack user")
public record AuthUserResponse(
        @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String login,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(format = "uri", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String avatarUrl,
        @ArraySchema(
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(allowableValues = {"USER", "ADMIN"}))
        List<String> roles) {
}
