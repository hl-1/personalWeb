package com.studystack.content.application.admin;

public sealed interface TaxonomyAdminCommand {

    String name();

    String slug();

    record Create(String name, String slug) implements TaxonomyAdminCommand {
    }

    record Update(String name, String slug, long version) implements TaxonomyAdminCommand {

        public Update {
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
        }
    }
}
