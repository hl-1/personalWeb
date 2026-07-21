package com.studystack.content.domain;

import java.util.Objects;

final class ContentFieldRules {

    private ContentFieldRules() {
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
}
