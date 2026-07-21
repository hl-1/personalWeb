package com.studystack.admin.web.preview;

import java.util.Objects;

public record AdminMarkdownPreviewResponse(String html) {

    public AdminMarkdownPreviewResponse {
        Objects.requireNonNull(html, "html is required");
    }
}
