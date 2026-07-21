package com.studystack.admin.web.preview;

import static com.studystack.admin.application.AdminMarkdownPreview.ARTICLE_MAX_LENGTH;
import static com.studystack.admin.application.AdminMarkdownPreview.PROJECT_MAX_LENGTH;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public sealed interface AdminMarkdownPreviewRequest {

    String markdown();

    record Article(
            @NotNull
            @Size(max = ARTICLE_MAX_LENGTH)
            String markdown) implements AdminMarkdownPreviewRequest {
    }

    record Project(
            @NotNull
            @Size(max = PROJECT_MAX_LENGTH)
            String markdown) implements AdminMarkdownPreviewRequest {
    }
}
