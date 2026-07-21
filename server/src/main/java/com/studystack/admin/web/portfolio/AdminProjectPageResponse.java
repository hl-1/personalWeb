package com.studystack.admin.web.portfolio;

import com.studystack.portfolio.application.admin.ProjectAdminPage;
import com.studystack.portfolio.application.admin.ProjectAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Administrative project page")
public record AdminProjectPageResponse(
        List<Summary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static AdminProjectPageResponse from(ProjectAdminPage result) {
        return new AdminProjectPageResponse(
                result.content().stream().map(Summary::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public record Summary(
            UUID id,
            String slug,
            String title,
            String summary,
            AdminProjectResponse.Status status,
            boolean featured,
            int sortOrder,
            Instant publishedAt,
            Instant updatedAt,
            long version) {

        private static Summary from(ProjectAdminView.Summary view) {
            return new Summary(
                    view.id(),
                    view.slug(),
                    view.title(),
                    view.summary(),
                    AdminProjectResponse.Status.valueOf(view.status().name()),
                    view.featured(),
                    view.sortOrder(),
                    view.publishedAt(),
                    view.updatedAt(),
                    view.version());
        }
    }
}
