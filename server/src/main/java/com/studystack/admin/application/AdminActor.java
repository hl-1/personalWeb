package com.studystack.admin.application;

import java.util.Objects;
import java.util.UUID;

public record AdminActor(UUID userId) {

    public AdminActor {
        Objects.requireNonNull(userId, "userId is required");
    }
}
