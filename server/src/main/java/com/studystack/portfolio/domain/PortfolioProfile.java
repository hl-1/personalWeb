package com.studystack.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "portfolio_profile")
public class PortfolioProfile {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "headline", nullable = false, length = 180)
    private String headline;

    @Column(name = "bio_markdown", nullable = false, length = 50_000)
    private String bioMarkdown;

    @Column(name = "seo_description", length = 160)
    private String seoDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PortfolioProfile() {
    }

    public PortfolioProfile(
            String displayName,
            String headline,
            String bioMarkdown,
            String seoDescription,
            Instant timestamp) {
        this.id = SINGLETON_ID;
        this.displayName = PortfolioFieldRules.requireText(displayName, 120, "displayName");
        this.headline = PortfolioFieldRules.requireText(headline, 180, "headline");
        this.bioMarkdown = PortfolioFieldRules.requireValue(bioMarkdown, 50_000, "bioMarkdown");
        this.seoDescription = PortfolioFieldRules.requireOptionalValue(
                seoDescription, 160, "seoDescription");
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.updatedAt = timestamp;
    }

    public Integer id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String headline() {
        return headline;
    }

    public String bioMarkdown() {
        return bioMarkdown;
    }

    public String seoDescription() {
        return seoDescription;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
