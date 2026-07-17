package com.studystack.shared.markdown;

import java.util.Objects;
import org.springframework.modulith.NamedInterface;

@NamedInterface("markdown")
public final class RenderedMarkdown {

    private final String html;

    RenderedMarkdown(String html) {
        this.html = Objects.requireNonNull(html, "html is required");
    }

    public String html() {
        return html;
    }
}
