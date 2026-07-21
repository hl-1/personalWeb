package com.studystack.portfolio.domain;

import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class PortfolioSlugConverter implements AttributeConverter<Slug, String> {

    private static final SlugPolicy POLICY = new SlugPolicy();

    @Override
    public String convertToDatabaseColumn(Slug attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Slug convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : POLICY.create(databaseValue);
    }
}
