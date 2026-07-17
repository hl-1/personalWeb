package com.studystack.portfolio.web;

import com.studystack.portfolio.application.PublicPortfolioQuery;
import com.studystack.shared.web.PublicProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio", description = "Public portfolio profile and work")
public final class PortfolioController {

    private final PublicPortfolioQuery query;

    public PortfolioController(PublicPortfolioQuery query) {
        this.query = query;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get the public portfolio profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public profile",
                    content = @Content(schema = @Schema(implementation = PortfolioProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class)))
    })
    public PortfolioProfileResponse profile() {
        return PortfolioProfileResponse.from(query.findProfile());
    }

    @GetMapping("/projects")
    @Operation(summary = "List published portfolio projects")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Published project page",
                    content = @Content(schema = @Schema(implementation = ProjectPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pagination",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class)))
    })
    public ProjectPageResponse projects(
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "10"))
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Boolean featured) {
        return ProjectPageResponse.from(query.findProjects(page, size, featured));
    }

    @GetMapping("/projects/{slug}")
    @Operation(summary = "Get a published portfolio project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Published project detail",
                    content = @Content(schema = @Schema(implementation = ProjectDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project is unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = PublicProblemResponse.class)))
    })
    public ProjectDetailResponse project(
            @Parameter(required = true, schema = @Schema(pattern = "[a-z0-9]+(?:-[a-z0-9]+)*"))
            @PathVariable String slug) {
        return ProjectDetailResponse.from(query.findProject(slug));
    }

    @GetMapping("/skills")
    @Operation(summary = "List visible portfolio skills")
    @ApiResponse(responseCode = "200", description = "Visible skills")
    public List<SkillResponse> skills() {
        return query.findSkills().stream().map(SkillResponse::from).toList();
    }

    @GetMapping("/experiences")
    @Operation(summary = "List visible portfolio experiences")
    @ApiResponse(responseCode = "200", description = "Visible experiences")
    public List<ExperienceResponse> experiences() {
        return query.findExperiences().stream().map(ExperienceResponse::from).toList();
    }
}
