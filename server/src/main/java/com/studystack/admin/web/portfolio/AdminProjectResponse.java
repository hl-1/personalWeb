package com.studystack.admin.web.portfolio;

import com.studystack.portfolio.application.admin.ProjectAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Administrative project detail")
public record AdminProjectResponse(
        UUID id,
        String slug,
        String title,
        String summary,
        String descriptionMarkdown,
        String projectUrl,
        String repositoryUrl,
        Status status,
        boolean featured,
        int sortOrder,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static AdminProjectResponse from(ProjectAdminView view) {
        return new AdminProjectResponse(
                view.id(),
                view.slug(),
                view.title(),
                view.summary(),
                view.descriptionMarkdown(),
                view.projectUrl(),
                view.repositoryUrl(),
                Status.valueOf(view.status().name()),
                view.featured(),
                view.sortOrder(),
                view.publishedAt(),
                view.createdAt(),
                view.updatedAt(),
                view.version());
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED;

        ProjectAdminView.Status toApplicationStatus() {
            return ProjectAdminView.Status.valueOf(name());
        }
    }
}
