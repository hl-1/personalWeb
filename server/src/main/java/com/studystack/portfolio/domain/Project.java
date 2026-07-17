package com.studystack.portfolio.domain;

import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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

    public void publish(Instant timestamp) {
        if (status != ProjectStatus.DRAFT) {
            throw new IllegalStateException("only draft projects can be published");
        }
        Instant publicationTime = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = ProjectStatus.PUBLISHED;
        this.publishedAt = publicationTime;
        this.updatedAt = publicationTime;
    }

    public void archive(Instant timestamp) {
        if (status != ProjectStatus.PUBLISHED) {
            throw new IllegalStateException("only published projects can be archived");
        }
        Instant archivedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = archivedAt;
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

final class PortfolioFieldRules {

    private PortfolioFieldRules() {
    }

    static String requireText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return requireValue(value, maximumLength, field);
    }

    static String requireValue(String value, int maximumLength, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
        return value;
    }

    static String requireOptionalValue(String value, int maximumLength, String field) {
        return value == null ? null : requireValue(value, maximumLength, field);
    }

    static int requireSortOrder(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("sortOrder must not be negative");
        }
        return value;
    }

    static void requireDateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate is required");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
    }
}

final class PortfolioUrlPolicy {

    private static final int MAXIMUM_LENGTH = 2_048;

    private PortfolioUrlPolicy() {
    }

    static String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()
                || candidate.length() > MAXIMUM_LENGTH
                || candidate.chars().anyMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character))) {
            throw invalidUrl(field);
        }

        try {
            URI parsed = new URI(candidate).normalize();
            if (!"https".equalsIgnoreCase(parsed.getScheme())
                    || parsed.getHost() == null
                    || parsed.getHost().isBlank()
                    || parsed.getRawUserInfo() != null) {
                throw invalidUrl(field);
            }
            String normalized = normalizedHttpsUrl(parsed);
            if (normalized.length() > MAXIMUM_LENGTH) {
                throw invalidUrl(field);
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw invalidUrl(field);
        }
    }

    private static String normalizedHttpsUrl(URI uri) {
        StringBuilder normalized = new StringBuilder("https://")
                .append(uri.getRawAuthority().toLowerCase(Locale.ROOT));
        append(normalized, uri.getRawPath(), null);
        append(normalized, uri.getRawQuery(), "?");
        append(normalized, uri.getRawFragment(), "#");
        return normalized.toString();
    }

    private static void append(StringBuilder target, String value, String prefix) {
        if (value != null) {
            if (prefix != null) {
                target.append(prefix);
            }
            target.append(value);
        }
    }

    private static IllegalArgumentException invalidUrl(String field) {
        return new IllegalArgumentException(field + " must be an HTTPS URL without credentials");
    }
}
