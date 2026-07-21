package com.studystack.portfolio.application.admin;

import java.util.List;
import java.util.Objects;

public record ProjectAdminPage(
        List<ProjectAdminView.Summary> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ProjectAdminPage {
        content = List.copyOf(Objects.requireNonNull(content, "content is required"));
    }
}
