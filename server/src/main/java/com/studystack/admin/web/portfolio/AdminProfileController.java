package com.studystack.admin.web.portfolio;

import com.studystack.admin.application.AdminPortfolioUseCase;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Profile;
import com.studystack.portfolio.application.admin.ProfileAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/portfolio/profile")
public final class AdminProfileController {

    private final AdminPortfolioUseCase portfolio;

    public AdminProfileController(AdminPortfolioUseCase portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    public Response find() {
        return Response.from(portfolio.findProfile());
    }

    @PutMapping
    public Response upsert(@Valid @RequestBody Request request) {
        return Response.from(portfolio.upsertProfile(request.toCommand()));
    }

    public record Request(
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 180) String headline,
            @NotNull @Size(max = 50_000) String bioMarkdown,
            @Size(max = 160) String seoDescription,
            @PositiveOrZero Long version) {

        ProfileAdminService.Command toCommand() {
            return new ProfileAdminService.Command(
                    displayName, headline, bioMarkdown, seoDescription, version);
        }
    }

    public record Response(
            int id,
            String displayName,
            String headline,
            String bioMarkdown,
            String seoDescription,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static Response from(Profile profile) {
            return new Response(
                    profile.id(),
                    profile.displayName(),
                    profile.headline(),
                    profile.bioMarkdown(),
                    profile.seoDescription(),
                    profile.createdAt(),
                    profile.updatedAt(),
                    profile.version());
        }
    }
}
