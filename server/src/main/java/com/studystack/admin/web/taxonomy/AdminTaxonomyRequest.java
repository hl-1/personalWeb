package com.studystack.admin.web.taxonomy;

import com.studystack.content.application.admin.TaxonomyAdminCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public sealed interface AdminTaxonomyRequest {

    String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    String name();

    String slug();

    record Create(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 3, max = 120) @Pattern(regexp = SLUG_PATTERN) String slug)
            implements AdminTaxonomyRequest {

        TaxonomyAdminCommand.Create toCommand() {
            return new TaxonomyAdminCommand.Create(name, slug);
        }
    }

    record Update(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 3, max = 120) @Pattern(regexp = SLUG_PATTERN) String slug,
            @NotNull @PositiveOrZero Long version)
            implements AdminTaxonomyRequest {

        TaxonomyAdminCommand.Update toCommand() {
            return new TaxonomyAdminCommand.Update(name, slug, version);
        }
    }
}
