package com.studystack.admin.web.article;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminVersionRequest(
        @NotNull
        @PositiveOrZero
        Long version) {
}
