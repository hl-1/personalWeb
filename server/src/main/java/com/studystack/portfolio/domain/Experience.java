package com.studystack.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "portfolio_experience")
public class Experience {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization", nullable = false, length = 180)
    private String organization;

    @Column(name = "role", nullable = false, length = 180)
    private String role;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "summary_markdown", nullable = false, length = 20_000)
    private String summaryMarkdown;

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

    protected Experience() {
    }

    public Experience(
            UUID id,
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String summaryMarkdown,
            int sortOrder,
            boolean visible,
            Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.organization = PortfolioFieldRules.requireText(organization, 180, "organization");
        this.role = PortfolioFieldRules.requireText(role, 180, "role");
        PortfolioFieldRules.requireDateRange(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.summaryMarkdown = PortfolioFieldRules.requireValue(
                summaryMarkdown, 20_000, "summaryMarkdown");
        this.sortOrder = PortfolioFieldRules.requireSortOrder(sortOrder);
        this.visible = visible;
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.updatedAt = timestamp;
    }

    public void revise(
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String summaryMarkdown,
            int sortOrder,
            boolean visible,
            Instant timestamp) {
        String revisedOrganization = PortfolioFieldRules.requireText(organization, 180, "organization");
        String revisedRole = PortfolioFieldRules.requireText(role, 180, "role");
        PortfolioFieldRules.requireDateRange(startDate, endDate);
        String revisedSummary = PortfolioFieldRules.requireValue(
                summaryMarkdown, 20_000, "summaryMarkdown");
        int revisedSortOrder = PortfolioFieldRules.requireSortOrder(sortOrder);
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.organization = revisedOrganization;
        this.role = revisedRole;
        this.startDate = startDate;
        this.endDate = endDate;
        this.summaryMarkdown = revisedSummary;
        this.sortOrder = revisedSortOrder;
        this.visible = visible;
        this.updatedAt = changedAt;
    }

    public UUID id() {
        return id;
    }

    public String organization() {
        return organization;
    }

    public String role() {
        return role;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public String summaryMarkdown() {
        return summaryMarkdown;
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
        return this == candidate || candidate instanceof Experience other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
