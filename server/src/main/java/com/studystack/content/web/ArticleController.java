package com.studystack.content.web;

import com.studystack.content.application.PublicArticleQuery;
import com.studystack.shared.web.PublicProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/articles")
@Tag(name = "Articles", description = "Published public articles")
public final class ArticleController {

    private final PublicArticleQuery query;

    public ArticleController(PublicArticleQuery query) {
        this.query = query;
    }

    @GetMapping
    @Operation(summary = "List published articles")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Published article page",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticlePageResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or taxonomy filter",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class)))
    })
    public ArticlePageResponse articles(
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "10"))
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(schema = @Schema(pattern = "[a-z0-9]+(?:-[a-z0-9]+)*"))
            @RequestParam(required = false) String category,
            @Parameter(schema = @Schema(pattern = "[a-z0-9]+(?:-[a-z0-9]+)*"))
            @RequestParam(required = false) String tag) {
        return ArticlePageResponse.from(query.findArticles(page, size, category, tag));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a published article")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Published article detail",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ArticleDetailResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Article is unavailable",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class)))
    })
    public ArticleDetailResponse article(
            @Parameter(required = true, schema = @Schema(pattern = "[a-z0-9]+(?:-[a-z0-9]+)*"))
            @PathVariable String slug) {
        return ArticleDetailResponse.from(query.findArticle(slug));
    }
}
