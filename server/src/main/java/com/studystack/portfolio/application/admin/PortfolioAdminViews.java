package com.studystack.portfolio.application.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PortfolioAdminViews {

    private PortfolioAdminViews() {
    }

    public record Profile(
            int id,
            String displayName,
            String headline,
            String bioMarkdown,
            String seoDescription,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record Skill(
            UUID id,
            String name,
            String category,
            String summary,
            int sortOrder,
            boolean visible,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record Experience(
            UUID id,
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String summaryMarkdown,
            int sortOrder,
            boolean visible,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public enum Failure {
        NOT_FOUND,
        STALE_VERSION
    }

    public static final class AdminException extends RuntimeException {

        private final Failure failure;

        AdminException(Failure failure) {
            super(switch (failure) {
                case NOT_FOUND -> "Portfolio resource was not found";
                case STALE_VERSION -> "Portfolio resource version is stale";
            });
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
