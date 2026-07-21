package com.studystack.admin.web.taxonomy;

import com.studystack.content.application.admin.TaxonomyAdminView;
import java.time.Instant;
import java.util.UUID;

public record AdminTaxonomyResponse(
        UUID id,
        String name,
        String slug,
        long articleCount,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static AdminTaxonomyResponse from(TaxonomyAdminView view) {
        return new AdminTaxonomyResponse(
                view.id(),
                view.name(),
                view.slug(),
                view.articleCount(),
                view.createdAt(),
                view.updatedAt(),
                view.version());
    }
}
