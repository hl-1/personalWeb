package com.studystack.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "portfolio_skill")
public class Skill {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "category", nullable = false, length = 120)
    private String category;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Skill() {
    }

    public Skill(
            UUID id,
            String name,
            String category,
            String summary,
            int sortOrder,
            boolean visible,
            Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.name = PortfolioFieldRules.requireText(name, 120, "name");
        this.category = PortfolioFieldRules.requireText(category, 120, "category");
        this.summary = PortfolioFieldRules.requireOptionalValue(summary, 500, "summary");
        this.sortOrder = PortfolioFieldRules.requireSortOrder(sortOrder);
        this.visible = visible;
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.updatedAt = timestamp;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public String summary() {
        return summary;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean visible() {
        return visible;
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

    @Override
    public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof Skill other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
