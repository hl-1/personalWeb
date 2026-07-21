package com.studystack.content.application.admin;

import java.util.List;
import java.util.Objects;

public record ArticleAdminPage(
        List<ArticleAdminView.Summary> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ArticleAdminPage {
        content = List.copyOf(Objects.requireNonNull(content, "content is required"));
    }
}
