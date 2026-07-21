package com.studystack.admin.web.article;

import com.studystack.content.application.admin.ArticleAdminCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public sealed interface AdminArticleRequest {

    String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    String slug();

    String title();

    String summary();

    String bodyMarkdown();

    UUID categoryId();

    List<UUID> tagIds();

    String seoTitle();

    String seoDescription();

    record Create(
            @NotBlank
            @Size(max = 120)
            @Pattern(regexp = SLUG_PATTERN)
            String slug,
            @NotBlank
            @Size(max = 180)
            String title,
            @NotBlank
            @Size(max = 500)
            String summary,
            @NotNull
            @Size(max = 200_000)
            String bodyMarkdown,
            UUID categoryId,
            @NotNull
            @Size(max = 10)
            List<@NotNull UUID> tagIds,
            @Size(max = 70)
            String seoTitle,
            @Size(max = 160)
            String seoDescription) implements AdminArticleRequest {

        ArticleAdminCommand.Create toCommand() {
            return new ArticleAdminCommand.Create(
                    slug,
                    title,
                    summary,
                    bodyMarkdown,
                    categoryId,
                    tagIds,
                    seoTitle,
                    seoDescription);
        }
    }

    record Update(
            @NotBlank
            @Size(max = 120)
            @Pattern(regexp = SLUG_PATTERN)
            String slug,
            @NotBlank
            @Size(max = 180)
            String title,
            @NotBlank
            @Size(max = 500)
            String summary,
            @NotNull
            @Size(max = 200_000)
            String bodyMarkdown,
            UUID categoryId,
            @NotNull
            @Size(max = 10)
            List<@NotNull UUID> tagIds,
            @Size(max = 70)
            String seoTitle,
            @Size(max = 160)
            String seoDescription,
            @NotNull
            @PositiveOrZero
            Long version) implements AdminArticleRequest {

        ArticleAdminCommand.Update toCommand() {
            return new ArticleAdminCommand.Update(
                    slug,
                    title,
                    summary,
                    bodyMarkdown,
                    categoryId,
                    tagIds,
                    seoTitle,
                    seoDescription,
                    version);
        }
    }
}
