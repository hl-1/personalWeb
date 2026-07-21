package com.studystack.admin.web.article;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record AdminPublishRequest(
        @NotNull
        @PositiveOrZero
        Long version,
        Instant publishAt) {
}
