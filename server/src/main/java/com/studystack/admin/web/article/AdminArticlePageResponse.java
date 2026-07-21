package com.studystack.admin.web.article;

import com.studystack.content.application.admin.ArticleAdminPage;
import com.studystack.content.application.admin.ArticleAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Administrative article page")
public record AdminArticlePageResponse(
        List<Summary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static AdminArticlePageResponse from(ArticleAdminPage result) {
        return new AdminArticlePageResponse(
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
            AdminArticleResponse.Status status,
            Instant publishedAt,
            Instant updatedAt,
            long version) {

        private static Summary from(ArticleAdminView.Summary view) {
            return new Summary(
                    view.id(),
                    view.slug(),
                    view.title(),
                    view.summary(),
                    AdminArticleResponse.Status.valueOf(view.status().name()),
                    view.publishedAt(),
                    view.updatedAt(),
                    view.version());
        }
    }
}
