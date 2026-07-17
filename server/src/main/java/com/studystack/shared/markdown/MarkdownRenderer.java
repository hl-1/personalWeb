package com.studystack.shared.markdown;

import org.springframework.modulith.NamedInterface;

@NamedInterface("markdown")
public interface MarkdownRenderer {

    RenderedMarkdown render(String markdown);
}
