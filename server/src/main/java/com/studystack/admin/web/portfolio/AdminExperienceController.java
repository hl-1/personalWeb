package com.studystack.admin.web.portfolio;

import com.studystack.admin.application.AdminPortfolioUseCase;
import com.studystack.portfolio.application.admin.ExperienceAdminService;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Experience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/admin/portfolio/experiences")
public final class AdminExperienceController {

    private final AdminPortfolioUseCase portfolio;

    public AdminExperienceController(AdminPortfolioUseCase portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    public List<Response> list() {
        return portfolio.listExperiences().stream().map(Response::from).toList();
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
        Response response = Response.from(portfolio.createExperience(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/portfolio/experiences/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public Response update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequest request) {
        return Response.from(portfolio.updateExperience(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam @PositiveOrZero long version) {
        portfolio.deleteExperience(id, version);
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(
            @NotBlank @Size(max = 180) String organization,
            @NotBlank @Size(max = 180) String role,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull @Size(max = 20_000) String summaryMarkdown,
            @NotNull @PositiveOrZero Integer sortOrder,
            @NotNull Boolean visible) {

        @AssertTrue(message = "endDate must not be before startDate")
        public boolean isDateRangeValid() {
            return startDate == null || endDate == null || !endDate.isBefore(startDate);
        }

        ExperienceAdminService.Create toCommand() {
            return new ExperienceAdminService.Create(
                    organization, role, startDate, endDate, summaryMarkdown, sortOrder, visible);
        }
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 180) String organization,
            @NotBlank @Size(max = 180) String role,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @NotNull @Size(max = 20_000) String summaryMarkdown,
            @NotNull @PositiveOrZero Integer sortOrder,
            @NotNull Boolean visible,
            @NotNull @PositiveOrZero Long version) {

        @AssertTrue(message = "endDate must not be before startDate")
        public boolean isDateRangeValid() {
            return startDate == null || endDate == null || !endDate.isBefore(startDate);
        }

        ExperienceAdminService.Update toCommand() {
            return new ExperienceAdminService.Update(
                    organization,
                    role,
                    startDate,
                    endDate,
                    summaryMarkdown,
                    sortOrder,
                    visible,
                    version);
        }
    }

    public record Response(
            UUID id,
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String summaryMarkdown,
            int sortOrder,
            boolean visible,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static Response from(Experience experience) {
            return new Response(
                    experience.id(),
                    experience.organization(),
                    experience.role(),
                    experience.startDate(),
                    experience.endDate(),
                    experience.summaryMarkdown(),
                    experience.sortOrder(),
                    experience.visible(),
                    experience.createdAt(),
                    experience.updatedAt(),
                    experience.version());
        }
    }
}
