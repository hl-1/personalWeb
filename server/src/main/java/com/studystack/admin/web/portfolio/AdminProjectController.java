package com.studystack.admin.web.portfolio;

import com.studystack.admin.application.AdminProjectUseCase;
import com.studystack.admin.web.article.AdminPublishRequest;
import com.studystack.admin.web.article.AdminVersionRequest;
import com.studystack.portfolio.application.admin.ProjectAdminView;
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
@RequestMapping("/api/v1/admin/portfolio/projects")
@Tag(name = "Admin Projects", description = "Administrative project management")
public final class AdminProjectController {

    private final AdminProjectUseCase projects;

    public AdminProjectController(AdminProjectUseCase projects) {
        this.projects = projects;
    }

    @GetMapping
    @Operation(summary = "List projects for administration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Administrative project page",
                    content = @Content(schema = @Schema(implementation = AdminProjectPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filters",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrator role required")
    })
    public AdminProjectPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) AdminProjectResponse.Status status,
            @RequestParam(required = false) String query) {
        ProjectAdminView.Status applicationStatus = status == null
                ? null
                : status.toApplicationStatus();
        return AdminProjectPageResponse.from(projects.list(page, size, applicationStatus, query));
    }

    @PostMapping
    @Operation(summary = "Create a draft project")
    public ResponseEntity<AdminProjectResponse> create(
            @Valid @RequestBody AdminProjectRequest.Create request) {
        AdminProjectResponse response = AdminProjectResponse.from(projects.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/portfolio/projects/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project for administration")
    public AdminProjectResponse find(@PathVariable UUID id) {
        return AdminProjectResponse.from(projects.find(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a project")
    public AdminProjectResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminProjectRequest.Update request) {
        return AdminProjectResponse.from(projects.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft project")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam @PositiveOrZero long version) {
        projects.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a draft project")
    public AdminProjectResponse publish(
            @PathVariable UUID id,
            @Valid @RequestBody AdminPublishRequest request) {
        return AdminProjectResponse.from(
                projects.publish(id, request.version(), request.publishAt()));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a published project")
    public AdminProjectResponse archive(
            @PathVariable UUID id,
            @Valid @RequestBody AdminVersionRequest request) {
        return AdminProjectResponse.from(projects.archive(id, request.version()));
    }
}
