package com.studystack.content.domain;

import com.studystack.shared.slug.Slug;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "content_category")
public class Category {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Convert(converter = ContentSlugConverter.class)
    @Column(name = "slug", nullable = false, length = 120)
    private Slug slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private Set<Article> articles = new LinkedHashSet<>();

    protected Category() {
    }

    public Category(UUID id, String name, Slug slug, Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.name = ContentFieldRules.requireText(name, 120, "name");
        this.slug = Objects.requireNonNull(slug, "slug is required");
        this.createdAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.updatedAt = timestamp;
    }

    public void update(String name, Slug slug, Instant timestamp) {
        String revisedName = ContentFieldRules.requireText(name, 120, "name");
        Slug revisedSlug = Objects.requireNonNull(slug, "slug is required");
        Instant changedAt = Objects.requireNonNull(timestamp, "timestamp is required");
        this.name = revisedName;
        this.slug = revisedSlug;
        this.updatedAt = changedAt;
    }

    void attachArticle(Article article) {
        articles.add(Objects.requireNonNull(article, "article is required"));
    }

    void detachArticle(Article article) {
        articles.remove(article);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Slug slug() {
        return slug;
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

    public Set<Article> articles() {
        return Collections.unmodifiableSet(articles);
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof Category other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
