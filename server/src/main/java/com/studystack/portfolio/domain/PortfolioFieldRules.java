package com.studystack.portfolio.domain;

import java.time.LocalDate;
import java.util.Objects;

final class PortfolioFieldRules {

    private PortfolioFieldRules() {
    }

    static String requireText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return requireValue(value, maximumLength, field);
    }

    static String requireValue(String value, int maximumLength, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
        return value;
    }

    static String requireOptionalValue(String value, int maximumLength, String field) {
        return value == null ? null : requireValue(value, maximumLength, field);
    }

    static int requireSortOrder(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("sortOrder must not be negative");
        }
        return value;
    }

    static void requireDateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate is required");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
    }
}
