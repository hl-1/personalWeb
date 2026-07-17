package com.studystack.portfolio.application;

import java.time.LocalDate;
import java.util.UUID;

public record ExperienceView(
        UUID id,
        String organization,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        String summaryHtml) {
}
