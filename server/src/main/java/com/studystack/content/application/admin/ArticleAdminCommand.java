package com.studystack.content.application.admin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public sealed interface ArticleAdminCommand {

    String slug();

    String title();

    String summary();

    String bodyMarkdown();

    UUID categoryId();

    List<UUID> tagIds();

    String seoTitle();

    String seoDescription();

    record Create(
            String slug,
            String title,
            String summary,
            String bodyMarkdown,
            UUID categoryId,
            List<UUID> tagIds,
            String seoTitle,
            String seoDescription) implements ArticleAdminCommand {

        public Create {
            tagIds = List.copyOf(Objects.requireNonNull(tagIds, "tagIds is required"));
        }
    }

    record Update(
            String slug,
            String title,
            String summary,
            String bodyMarkdown,
            UUID categoryId,
            List<UUID> tagIds,
            String seoTitle,
            String seoDescription,
            long version) implements ArticleAdminCommand {

        public Update {
            tagIds = List.copyOf(Objects.requireNonNull(tagIds, "tagIds is required"));
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
        }
    }
}
