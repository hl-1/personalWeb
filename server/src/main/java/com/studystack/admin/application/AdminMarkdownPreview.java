package com.studystack.admin.application;

import com.studystack.shared.markdown.MarkdownRenderer;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class AdminMarkdownPreview {

    public static final int ARTICLE_MAX_LENGTH = 200_000;
    public static final int PROJECT_MAX_LENGTH = 100_000;

    private final MarkdownRenderer markdownRenderer;

    AdminMarkdownPreview(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = Objects.requireNonNull(markdownRenderer, "markdownRenderer is required");
    }

    public String previewArticle(String markdown) {
        return preview(markdown, ARTICLE_MAX_LENGTH);
    }

    public String previewProject(String markdown) {
        return preview(markdown, PROJECT_MAX_LENGTH);
    }

    private String preview(String markdown, int maximumLength) {
        Objects.requireNonNull(markdown, "markdown is required");
        if (markdown.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "markdown must not exceed " + maximumLength + " characters");
        }
        return markdownRenderer.render(markdown).html();
    }
}
