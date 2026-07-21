package com.studystack.portfolio.domain;

import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "portfolio_project")
public class Project {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Convert(converter = PortfolioSlugConverter.class)
    @Column(name = "slug", nullable = false, length = 120)
    private Slug slug;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "description_markdown", nullable = false, length = 100_000)
    private String descriptionMarkdown;

    @Column(name = "project_url", length = 2_048)
    private String projectUrl;

    @Column(name = "repository_url", length = 2_048)
    private String repositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProjectStatus status;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Project() {
    }

    public Project(
            UUID id,
            Slug slug,
            String title,
            String summary,
            String descriptionMarkdown,
            String projectUrl,
            String repositoryUrl,
            boolean featured,
            int sortOrder,
            Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.slug = Objects.requireNonNull(slug, "slug is required");
        this.title = PortfolioFieldRules.requireText(title, 180, "title");
        this.summary = PortfolioFieldRules.requireText(summary, 500, "summary");
        this.descriptionMarkdown = PortfolioFieldRules.requireValue(
                descriptionMarkdown, 100_000, "descriptionMarkdown");
        this.projectUrl = PortfolioUrlPolicy.normalizeOptional(projectUrl, "projectUrl");
        this.repositoryUrl = PortfolioUrlPolicy.normalizeOptional(repositoryUrl, "repositoryUrl");
        this.sortOrder = PortfolioFieldRules.requireSortOrder(sortOrder);
        this.featured = featured;
        this.status = ProjectStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.updatedAt = timestamp;
    }

    public void changeSlug(String requestedValue, SlugPolicy policy, Instant timestamp) {
        Objects.requireNonNull(policy, "policy is required");
        if (publishedAt != null) {
            policy.requireUnchangedAfterPublication(slug, requestedValue);
            return;
        }
        Slug requestedSlug = policy.create(requestedValue);
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.slug = requestedSlug;
        this.updatedAt = changedAt;
    }

    public void changePresentation(boolean featured, int sortOrder, Instant timestamp) {
        int changedSortOrder = PortfolioFieldRules.requireSortOrder(sortOrder);
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.featured = featured;
        this.sortOrder = changedSortOrder;
        this.updatedAt = changedAt;
    }

    public void revise(
            String title,
            String summary,
            String descriptionMarkdown,
            String projectUrl,
            String repositoryUrl,
            boolean featured,
            int sortOrder,
            Instant timestamp) {
        String revisedTitle = PortfolioFieldRules.requireText(title, 180, "title");
        String revisedSummary = PortfolioFieldRules.requireText(summary, 500, "summary");
        String revisedDescription = PortfolioFieldRules.requireValue(
                descriptionMarkdown, 100_000, "descriptionMarkdown");
        String revisedProjectUrl = PortfolioUrlPolicy.normalizeOptional(projectUrl, "projectUrl");
        String revisedRepositoryUrl = PortfolioUrlPolicy.normalizeOptional(repositoryUrl, "repositoryUrl");
        int revisedSortOrder = PortfolioFieldRules.requireSortOrder(sortOrder);
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.title = revisedTitle;
        this.summary = revisedSummary;
        this.descriptionMarkdown = revisedDescription;
        this.projectUrl = revisedProjectUrl;
        this.repositoryUrl = revisedRepositoryUrl;
        this.featured = featured;
        this.sortOrder = revisedSortOrder;
        this.updatedAt = changedAt;
    }

    public void publish(Instant timestamp) {
        publish(timestamp, timestamp);
    }

    public void publish(Instant publicationTime, Instant timestamp) {
        if (status != ProjectStatus.DRAFT) {
            throw new IllegalStateException("only draft projects can be published");
        }
        Instant publishedAt = Objects.requireNonNull(publicationTime, "publicationTime is required");
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = ProjectStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.updatedAt = changedAt;
    }

    public void archive(Instant timestamp) {
        if (status != ProjectStatus.PUBLISHED) {
            throw new IllegalStateException("only published projects can be archived");
        }
        Instant archivedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = archivedAt;
    }

    public void requireEditable() {
        if (status == ProjectStatus.ARCHIVED) {
            throw new IllegalStateException("archived projects cannot be edited");
        }
    }

    public void requireDraftDeletion() {
        if (status != ProjectStatus.DRAFT) {
            throw new IllegalStateException("only draft projects can be deleted");
        }
    }

    public UUID id() {
        return id;
    }

    public Slug slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public String descriptionMarkdown() {
        return descriptionMarkdown;
    }

    public String projectUrl() {
        return projectUrl;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }

    public ProjectStatus status() {
        return status;
    }

    public boolean featured() {
        return featured;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public Instant publishedAt() {
        return publishedAt;
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
        return this == candidate || candidate instanceof Project other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
