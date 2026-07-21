package com.studystack.content.domain;

import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "content_article")
public class Article {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Convert(converter = ContentSlugConverter.class)
    @Column(name = "slug", nullable = false, length = 120)
    private Slug slug;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "body_markdown", nullable = false, length = 200_000)
    private String bodyMarkdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ArticleStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "content_article_tag",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "seo_title", length = 70)
    private String seoTitle;

    @Column(name = "seo_description", length = 160)
    private String seoDescription;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Article() {
    }

    public Article(
            UUID id,
            Slug slug,
            String title,
            String summary,
            String bodyMarkdown,
            Category category,
            String seoTitle,
            String seoDescription,
            Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.slug = Objects.requireNonNull(slug, "slug is required");
        this.title = ContentFieldRules.requireText(title, 180, "title");
        this.summary = ContentFieldRules.requireText(summary, 500, "summary");
        this.bodyMarkdown = ContentFieldRules.requireValue(bodyMarkdown, 200_000, "bodyMarkdown");
        this.seoTitle = ContentFieldRules.requireOptionalValue(seoTitle, 70, "seoTitle");
        this.seoDescription = ContentFieldRules.requireOptionalValue(seoDescription, 160, "seoDescription");
        this.status = ArticleStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.updatedAt = timestamp;
        changeCategoryInternal(category);
    }

    public void revise(
            String title,
            String summary,
            String bodyMarkdown,
            String seoTitle,
            String seoDescription,
            Instant timestamp) {
        String revisedTitle = ContentFieldRules.requireText(title, 180, "title");
        String revisedSummary = ContentFieldRules.requireText(summary, 500, "summary");
        String revisedBody = ContentFieldRules.requireValue(bodyMarkdown, 200_000, "bodyMarkdown");
        String revisedSeoTitle = ContentFieldRules.requireOptionalValue(seoTitle, 70, "seoTitle");
        String revisedSeoDescription = ContentFieldRules.requireOptionalValue(
                seoDescription, 160, "seoDescription");
        Instant revisedAt = Objects.requireNonNull(timestamp, "timestamp is required");

        this.title = revisedTitle;
        this.summary = revisedSummary;
        this.bodyMarkdown = revisedBody;
        this.seoTitle = revisedSeoTitle;
        this.seoDescription = revisedSeoDescription;
        this.updatedAt = revisedAt;
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

    public void publish(Instant timestamp) {
        publish(timestamp, timestamp);
    }

    public void publish(Instant publicationTime, Instant timestamp) {
        if (status != ArticleStatus.DRAFT) {
            throw new IllegalStateException("only draft articles can be published");
        }
        Instant publishedAt = Objects.requireNonNull(publicationTime, "publicationTime is required");
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = ArticleStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.updatedAt = changedAt;
    }

    public void archive(Instant timestamp) {
        if (status != ArticleStatus.PUBLISHED) {
            throw new IllegalStateException("only published articles can be archived");
        }
        Instant archivedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.status = ArticleStatus.ARCHIVED;
        this.updatedAt = archivedAt;
    }

    public void changeCategory(Category newCategory, Instant timestamp) {
        if (Objects.equals(category, newCategory)) {
            return;
        }
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        changeCategoryInternal(newCategory);
        this.updatedAt = changedAt;
    }

    public void addTag(Tag tag, Instant timestamp) {
        Tag requiredTag = Objects.requireNonNull(tag, "tag is required");
        if (tags.contains(requiredTag)) {
            throw new IllegalArgumentException("tag is already assigned to article");
        }
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        tags.add(requiredTag);
        requiredTag.attachArticle(this);
        this.updatedAt = changedAt;
    }

    public void removeTag(Tag tag, Instant timestamp) {
        if (tag != null && tags.contains(tag)) {
            Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
            tags.remove(tag);
            tag.detachArticle(this);
            this.updatedAt = changedAt;
        }
    }

    public void replaceTags(Set<Tag> replacements, Instant timestamp) {
        Set<Tag> revisedTags = new LinkedHashSet<>(Objects.requireNonNull(replacements, "tags are required"));
        revisedTags.forEach(tag -> Objects.requireNonNull(tag, "tag is required"));
        if (tags.equals(revisedTags)) {
            return;
        }
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        for (Tag tag : new LinkedHashSet<>(tags)) {
            if (!revisedTags.contains(tag)) {
                tags.remove(tag);
                tag.detachArticle(this);
            }
        }
        for (Tag tag : revisedTags) {
            if (tags.add(tag)) {
                tag.attachArticle(this);
            }
        }
        this.updatedAt = changedAt;
    }

    public void requireEditable() {
        if (status == ArticleStatus.ARCHIVED) {
            throw new IllegalStateException("archived articles cannot be edited");
        }
    }

    public void requireDraftDeletion() {
        if (status != ArticleStatus.DRAFT) {
            throw new IllegalStateException("only draft articles can be deleted");
        }
    }

    private void changeCategoryInternal(Category newCategory) {
        if (category != null) {
            category.detachArticle(this);
        }
        this.category = newCategory;
        if (newCategory != null) {
            newCategory.attachArticle(this);
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

    public String bodyMarkdown() {
        return bodyMarkdown;
    }

    public ArticleStatus status() {
        return status;
    }

    public Category category() {
        return category;
    }

    public Set<Tag> tags() {
        return Collections.unmodifiableSet(tags);
    }

    public String seoTitle() {
        return seoTitle;
    }

    public String seoDescription() {
        return seoDescription;
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
        return this == candidate || candidate instanceof Article other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
