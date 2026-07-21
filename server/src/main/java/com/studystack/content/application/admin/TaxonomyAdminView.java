package com.studystack.content.application.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaxonomyAdminView(
        UUID id,
        String name,
        String slug,
        long articleCount,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public TaxonomyAdminView {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(slug, "slug is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
