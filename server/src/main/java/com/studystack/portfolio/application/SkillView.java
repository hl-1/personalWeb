package com.studystack.portfolio.application;

import java.util.UUID;

public record SkillView(
        UUID id,
        String name,
        String category,
        String summary) {
}
