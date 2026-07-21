package com.studystack.admin.web.preview;

import com.studystack.admin.application.AdminMarkdownPreview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin Markdown Preview", description = "Safe administrative Markdown previews")
public final class AdminMarkdownPreviewController {

    private final AdminMarkdownPreview preview;

    public AdminMarkdownPreviewController(AdminMarkdownPreview preview) {
        this.preview = preview;
    }

    @PostMapping("/api/v1/admin/articles/preview")
    @Operation(summary = "Preview article Markdown")
    public ResponseEntity<AdminMarkdownPreviewResponse> previewArticle(
            @Valid @RequestBody AdminMarkdownPreviewRequest.Article request) {
        return response(preview.previewArticle(request.markdown()));
    }

    @PostMapping("/api/v1/admin/portfolio/projects/preview")
    @Operation(summary = "Preview project Markdown")
    public ResponseEntity<AdminMarkdownPreviewResponse> previewProject(
            @Valid @RequestBody AdminMarkdownPreviewRequest.Project request) {
        return response(preview.previewProject(request.markdown()));
    }

    private ResponseEntity<AdminMarkdownPreviewResponse> response(String html) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AdminMarkdownPreviewResponse(html));
    }
}
