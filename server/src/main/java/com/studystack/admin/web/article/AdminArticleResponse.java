package com.studystack.admin.web.article;

import com.studystack.content.application.admin.ArticleAdminView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Administrative article detail")
public record AdminArticleResponse(
        UUID id,
        String slug,
        String title,
        String summary,
        String bodyMarkdown,
        Status status,
        UUID categoryId,
        List<UUID> tagIds,
        String seoTitle,
        String seoDescription,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static AdminArticleResponse from(ArticleAdminView view) {
        return new AdminArticleResponse(
                view.id(),
                view.slug(),
                view.title(),
                view.summary(),
                view.bodyMarkdown(),
                Status.valueOf(view.status().name()),
                view.categoryId(),
                view.tagIds(),
                view.seoTitle(),
                view.seoDescription(),
                view.publishedAt(),
                view.createdAt(),
                view.updatedAt(),
                view.version());
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED;

        ArticleAdminView.Status toApplicationStatus() {
            return ArticleAdminView.Status.valueOf(name());
        }
    }
}
