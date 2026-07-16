package com.studystack.identity.application;

import java.util.Objects;

public final class InvalidGitHubClaimsException extends RuntimeException {

    private final String fieldName;
    private final Reason reason;

    public InvalidGitHubClaimsException(String fieldName, Reason reason) {
        super("Invalid GitHub claim: "
                + Objects.requireNonNull(fieldName, "fieldName is required")
                + " ("
                + Objects.requireNonNull(reason, "reason is required")
                + ")");
        this.fieldName = fieldName;
        this.reason = reason;
    }

    public String fieldName() {
        return fieldName;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        REQUIRED,
        INVALID_TYPE,
        INVALID_VALUE,
        TOO_LONG
    }
}
