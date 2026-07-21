package com.studystack.admin.web.article;

import com.studystack.admin.application.AdminArticleUseCase;
import com.studystack.content.application.admin.ArticleAdminView;
import com.studystack.shared.web.PublicProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/articles")
@Tag(name = "Admin Articles", description = "Administrative article management")
public final class AdminArticleController {

    private final AdminArticleUseCase articles;

    public AdminArticleController(AdminArticleUseCase articles) {
        this.articles = articles;
    }

    @GetMapping
    @Operation(summary = "List articles for administration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrative article page",
                    content = @Content(schema = @Schema(implementation = AdminArticlePageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filters",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required")
    })
    public AdminArticlePageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) AdminArticleResponse.Status status,
            @RequestParam(required = false) String query) {
        ArticleAdminView.Status applicationStatus = status == null
                ? null
                : status.toApplicationStatus();
        return AdminArticlePageResponse.from(articles.list(page, size, applicationStatus, query));
    }

    @PostMapping
    @Operation(summary = "Create a draft article")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Article created",
                    content = @Content(schema = @Schema(implementation = AdminArticleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid article"),
            @ApiResponse(responseCode = "404", description = "Taxonomy not found"),
            @ApiResponse(responseCode = "409", description = "Article slug conflict")
    })
    public ResponseEntity<AdminArticleResponse> create(
            @Valid @RequestBody AdminArticleRequest.Create request) {
        AdminArticleResponse response = AdminArticleResponse.from(articles.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/articles/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an article for administration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrative article detail",
                    content = @Content(schema = @Schema(implementation = AdminArticleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid article id"),
            @ApiResponse(responseCode = "404", description = "Article not found")
    })
    public AdminArticleResponse find(@PathVariable UUID id) {
        return AdminArticleResponse.from(articles.find(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an article")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article updated",
                    content = @Content(schema = @Schema(implementation = AdminArticleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid article"),
            @ApiResponse(responseCode = "404", description = "Article or taxonomy not found"),
            @ApiResponse(responseCode = "409", description = "Version, slug, or state conflict")
    })
    public AdminArticleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminArticleRequest.Update request) {
        return AdminArticleResponse.from(articles.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft article")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Article deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid article id or version"),
            @ApiResponse(responseCode = "404", description = "Article not found"),
            @ApiResponse(responseCode = "409", description = "Version or state conflict")
    })
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam @PositiveOrZero long version) {
        articles.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a draft article")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article published",
                    content = @Content(schema = @Schema(implementation = AdminArticleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid command"),
            @ApiResponse(responseCode = "404", description = "Article not found"),
            @ApiResponse(responseCode = "409", description = "Version or state conflict")
    })
    public AdminArticleResponse publish(
            @PathVariable UUID id,
            @Valid @RequestBody AdminPublishRequest request) {
        return AdminArticleResponse.from(
                articles.publish(id, request.version(), request.publishAt()));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a published article")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article archived",
                    content = @Content(schema = @Schema(implementation = AdminArticleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid command"),
            @ApiResponse(responseCode = "404", description = "Article not found"),
            @ApiResponse(responseCode = "409", description = "Version or state conflict")
    })
    public AdminArticleResponse archive(
            @PathVariable UUID id,
            @Valid @RequestBody AdminVersionRequest request) {
        return AdminArticleResponse.from(articles.archive(id, request.version()));
    }
}
