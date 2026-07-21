package com.studystack.admin.web.portfolio;

import com.studystack.portfolio.application.admin.ProjectAdminCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public sealed interface AdminProjectRequest {

    String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";
    String HTTPS_URL_PATTERN = "(?i)https://[^\\s/@?#]+(?::\\d+)?(?:[/?#][^\\s]*)?";

    String slug();

    String title();

    String summary();

    String descriptionMarkdown();

    String projectUrl();

    String repositoryUrl();

    boolean featured();

    int sortOrder();

    record Create(
            @NotBlank
            @Size(min = 3, max = 120)
            @Pattern(regexp = SLUG_PATTERN)
            String slug,
            @NotBlank
            @Size(max = 180)
            String title,
            @NotBlank
            @Size(max = 500)
            String summary,
            @NotNull
            @Size(max = 100_000)
            String descriptionMarkdown,
            @Size(max = 2_048)
            @Pattern(regexp = HTTPS_URL_PATTERN)
            String projectUrl,
            @Size(max = 2_048)
            @Pattern(regexp = HTTPS_URL_PATTERN)
            String repositoryUrl,
            boolean featured,
            @PositiveOrZero
            int sortOrder) implements AdminProjectRequest {

        ProjectAdminCommand.Create toCommand() {
            return new ProjectAdminCommand.Create(
                    slug,
                    title,
                    summary,
                    descriptionMarkdown,
                    projectUrl,
                    repositoryUrl,
                    featured,
                    sortOrder);
        }
    }

    record Update(
            @NotBlank
            @Size(min = 3, max = 120)
            @Pattern(regexp = SLUG_PATTERN)
            String slug,
            @NotBlank
            @Size(max = 180)
            String title,
            @NotBlank
            @Size(max = 500)
            String summary,
            @NotNull
            @Size(max = 100_000)
            String descriptionMarkdown,
            @Size(max = 2_048)
            @Pattern(regexp = HTTPS_URL_PATTERN)
            String projectUrl,
            @Size(max = 2_048)
            @Pattern(regexp = HTTPS_URL_PATTERN)
            String repositoryUrl,
            boolean featured,
            @PositiveOrZero
            int sortOrder,
            @NotNull
            @PositiveOrZero
            Long version) implements AdminProjectRequest {

        ProjectAdminCommand.Update toCommand() {
            return new ProjectAdminCommand.Update(
                    slug,
                    title,
                    summary,
                    descriptionMarkdown,
                    projectUrl,
                    repositoryUrl,
                    featured,
                    sortOrder,
                    version);
        }
    }
}
