package com.studystack.portfolio.application.admin;

public sealed interface ProjectAdminCommand {

    String slug();

    String title();

    String summary();

    String descriptionMarkdown();

    String projectUrl();

    String repositoryUrl();

    boolean featured();

    int sortOrder();

    record Create(
            String slug,
            String title,
            String summary,
            String descriptionMarkdown,
            String projectUrl,
            String repositoryUrl,
            boolean featured,
            int sortOrder) implements ProjectAdminCommand {
    }

    record Update(
            String slug,
            String title,
            String summary,
            String descriptionMarkdown,
            String projectUrl,
            String repositoryUrl,
            boolean featured,
            int sortOrder,
            long version) implements ProjectAdminCommand {

        public Update {
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
        }
    }
}
