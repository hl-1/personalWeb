package com.studystack.content.application;

import java.util.Objects;

public record TaxonomySummary(
        String name,
        String slug,
        long publishedArticleCount) {

    public TaxonomySummary {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(slug, "slug is required");
        if (publishedArticleCount < 1) {
            throw new IllegalArgumentException("publishedArticleCount must be positive");
        }
    }
}
