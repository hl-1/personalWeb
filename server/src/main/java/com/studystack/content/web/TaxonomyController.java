package com.studystack.content.web;

import com.studystack.content.application.PublicArticleQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Taxonomies", description = "Public article categories and tags")
public final class TaxonomyController {

    private final PublicArticleQuery query;

    public TaxonomyController(PublicArticleQuery query) {
        this.query = query;
    }

    @GetMapping("/api/v1/categories")
    @Operation(summary = "List public article categories")
    @ApiResponse(
            responseCode = "200",
            description = "Categories used by published articles",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = TaxonomyResponse.class))))
    public List<TaxonomyResponse> categories() {
        return query.findCategories().stream().map(TaxonomyResponse::from).toList();
    }

    @GetMapping("/api/v1/tags")
    @Operation(summary = "List public article tags")
    @ApiResponse(
            responseCode = "200",
            description = "Tags used by published articles",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = TaxonomyResponse.class))))
    public List<TaxonomyResponse> tags() {
        return query.findTags().stream().map(TaxonomyResponse::from).toList();
    }
}
