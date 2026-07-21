package com.studystack.admin.web.portfolio;

import com.studystack.admin.application.AdminPortfolioUseCase;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Skill;
import com.studystack.portfolio.application.admin.SkillAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/admin/portfolio/skills")
public final class AdminSkillController {

    private final AdminPortfolioUseCase portfolio;

    public AdminSkillController(AdminPortfolioUseCase portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    public List<Response> list() {
        return portfolio.listSkills().stream().map(Response::from).toList();
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
        Response response = Response.from(portfolio.createSkill(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/portfolio/skills/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public Response update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequest request) {
        return Response.from(portfolio.updateSkill(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam @PositiveOrZero long version) {
        portfolio.deleteSkill(id, version);
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String category,
            @Size(max = 500) String summary,
            @NotNull @PositiveOrZero Integer sortOrder,
            @NotNull Boolean visible) {

        SkillAdminService.Create toCommand() {
            return new SkillAdminService.Create(name, category, summary, sortOrder, visible);
        }
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String category,
            @Size(max = 500) String summary,
            @NotNull @PositiveOrZero Integer sortOrder,
            @NotNull Boolean visible,
            @NotNull @PositiveOrZero Long version) {

        SkillAdminService.Update toCommand() {
            return new SkillAdminService.Update(name, category, summary, sortOrder, visible, version);
        }
    }

    public record Response(
            UUID id,
            String name,
            String category,
            String summary,
            int sortOrder,
            boolean visible,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static Response from(Skill skill) {
            return new Response(
                    skill.id(),
                    skill.name(),
                    skill.category(),
                    skill.summary(),
                    skill.sortOrder(),
                    skill.visible(),
                    skill.createdAt(),
                    skill.updatedAt(),
                    skill.version());
        }
    }
}
