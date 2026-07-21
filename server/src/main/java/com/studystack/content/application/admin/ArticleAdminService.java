package com.studystack.content.application.admin;

import com.studystack.content.domain.Article;
import com.studystack.content.domain.ArticleRepository;
import com.studystack.content.domain.Category;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.Tag;
import com.studystack.content.domain.TagRepository;
import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ArticleAdminService {

    private static final int MAXIMUM_TAGS = 10;
    private static final int MAXIMUM_PAGE_SIZE = 100;
    private static final int MAXIMUM_QUERY_LENGTH = 100;
    private static final String ARTICLE_SLUG_CONSTRAINT = "uk_content_article_slug";

    private final ArticleRepository articles;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final SlugPolicy slugPolicy;

    ArticleAdminService(
            ArticleRepository articles,
            CategoryRepository categories,
            TagRepository tags,
            SlugPolicy slugPolicy) {
        this.articles = Objects.requireNonNull(articles, "articles is required");
        this.categories = Objects.requireNonNull(categories, "categories is required");
        this.tags = Objects.requireNonNull(tags, "tags is required");
        this.slugPolicy = Objects.requireNonNull(slugPolicy, "slugPolicy is required");
    }

    @Transactional(readOnly = true)
    public ArticleAdminPage list(
            int page,
            int size,
            ArticleAdminView.Status status,
            String query) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        String normalizedQuery = normalizeQuery(query);
        String statusValue = status == null ? null : status.name();
        long totalElements = articles.countAdminArticles(statusValue, normalizedQuery);
        List<ArticleAdminView.Summary> content = articles.findAdminArticles(
                        statusValue, normalizedQuery, size, (long) page * size).stream()
                .map(this::toSummary)
                .toList();
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new ArticleAdminPage(content, page, size, totalElements, totalPages);
    }

    public ArticleAdminView create(ArticleAdminCommand.Create command) {
        Objects.requireNonNull(command, "command is required");
        Slug slug = slugPolicy.create(command.slug());
        requireUniqueSlug(slug, null);
        ResolvedTaxonomy taxonomy = resolveTaxonomy(command.categoryId(), command.tagIds());
        Instant timestamp = now();
        Article article = new Article(
                UUID.randomUUID(),
                slug,
                command.title(),
                command.summary(),
                command.bodyMarkdown(),
                taxonomy.category(),
                command.seoTitle(),
                command.seoDescription(),
                timestamp);
        article.replaceTags(taxonomy.tags(), timestamp);
        return persist(article);
    }

    @Transactional(readOnly = true)
    public ArticleAdminView find(UUID id) {
        return toView(findArticle(id));
    }

    public ArticleAdminView update(UUID id, ArticleAdminCommand.Update command) {
        Objects.requireNonNull(command, "command is required");
        Article article = findArticle(id);
        requireVersion(article, command.version());
        try {
            article.requireEditable();
            Slug requestedSlug = slugPolicy.create(command.slug());
            requireUniqueSlug(requestedSlug, article.id());
            ResolvedTaxonomy taxonomy = resolveTaxonomy(command.categoryId(), command.tagIds());
            Instant timestamp = now();
            article.changeSlug(requestedSlug.value(), slugPolicy, timestamp);
            article.revise(
                    command.title(),
                    command.summary(),
                    command.bodyMarkdown(),
                    command.seoTitle(),
                    command.seoDescription(),
                    timestamp);
            article.changeCategory(taxonomy.category(), timestamp);
            article.replaceTags(taxonomy.tags(), timestamp);
            return persist(article);
        } catch (IllegalStateException exception) {
            throw failure(Failure.INVALID_STATE_TRANSITION);
        }
    }

    public void delete(UUID id, long version) {
        Article article = findArticle(id);
        requireVersion(article, version);
        try {
            article.requireDraftDeletion();
        } catch (IllegalStateException exception) {
            throw failure(Failure.DRAFT_DELETE_ONLY);
        }
        try {
            articles.delete(article);
            articles.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    public ArticleAdminView publish(UUID id, long version, Instant publishAt) {
        Article article = findArticle(id);
        requireVersion(article, version);
        Instant timestamp = now();
        Instant publicationTime = publishAt == null
                ? timestamp
                : publishAt.truncatedTo(ChronoUnit.MICROS);
        try {
            article.publish(publicationTime, timestamp);
            return persist(article);
        } catch (IllegalStateException exception) {
            throw failure(Failure.INVALID_STATE_TRANSITION);
        }
    }

    public ArticleAdminView archive(UUID id, long version) {
        Article article = findArticle(id);
        requireVersion(article, version);
        try {
            article.archive(now());
            return persist(article);
        } catch (IllegalStateException exception) {
            throw failure(Failure.INVALID_STATE_TRANSITION);
        }
    }

    private ArticleAdminView persist(Article article) {
        try {
            articles.save(article);
            articles.flush();
            return toView(article);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, ARTICLE_SLUG_CONSTRAINT)) {
                throw failure(Failure.DUPLICATE_SLUG);
            }
            throw exception;
        }
    }

    private Article findArticle(UUID id) {
        Objects.requireNonNull(id, "id is required");
        return articles.findAdminById(id).orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private void requireVersion(Article article, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (article.version() == null || article.version() != expectedVersion) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private void requireUniqueSlug(Slug slug, UUID currentArticleId) {
        articles.findBySlug(slug).ifPresent(existing -> {
            if (!existing.id().equals(currentArticleId)) {
                throw failure(Failure.DUPLICATE_SLUG);
            }
        });
    }

    private ResolvedTaxonomy resolveTaxonomy(UUID categoryId, List<UUID> requestedTagIds) {
        Objects.requireNonNull(requestedTagIds, "tagIds is required");
        if (requestedTagIds.size() > MAXIMUM_TAGS) {
            throw new IllegalArgumentException("an article can have at most 10 tags");
        }
        LinkedHashSet<UUID> uniqueTagIds = new LinkedHashSet<>(requestedTagIds);
        if (uniqueTagIds.size() != requestedTagIds.size()) {
            throw new IllegalArgumentException("tagIds must be unique");
        }

        Category category = categoryId == null
                ? null
                : categories.findById(categoryId).orElseThrow(() -> failure(Failure.NOT_FOUND));
        List<Tag> loadedTags = uniqueTagIds.isEmpty()
                ? List.of()
                : tags.findAllByIdIn(uniqueTagIds);
        if (loadedTags.size() != uniqueTagIds.size()) {
            throw failure(Failure.NOT_FOUND);
        }
        return new ResolvedTaxonomy(category, new LinkedHashSet<>(loadedTags));
    }

    private ArticleAdminView toView(Article article) {
        List<UUID> tagIds = article.tags().stream()
                .map(Tag::id)
                .sorted()
                .toList();
        return new ArticleAdminView(
                article.id(),
                article.slug().value(),
                article.title(),
                article.summary(),
                article.bodyMarkdown(),
                ArticleAdminView.Status.valueOf(article.status().name()),
                article.category() == null ? null : article.category().id(),
                tagIds,
                article.seoTitle(),
                article.seoDescription(),
                article.publishedAt(),
                article.createdAt(),
                article.updatedAt(),
                article.version());
    }

    private ArticleAdminView.Summary toSummary(Article article) {
        return new ArticleAdminView.Summary(
                article.id(),
                article.slug().value(),
                article.title(),
                article.summary(),
                ArticleAdminView.Status.valueOf(article.status().name()),
                article.publishedAt(),
                article.updatedAt(),
                article.version());
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAXIMUM_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must not exceed 100 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean hasConstraint(Throwable failure, String constraintName) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException constraint
                    && constraintName.equals(constraint.getConstraintName())) {
                return true;
            }
            if (current == current.getCause()) {
                return false;
            }
        }
        return false;
    }

    private static ArticleAdminException failure(Failure failure) {
        return new ArticleAdminException(failure);
    }

    private record ResolvedTaxonomy(Category category, Set<Tag> tags) {
    }

    public enum Failure {
        NOT_FOUND,
        DUPLICATE_SLUG,
        STALE_VERSION,
        INVALID_STATE_TRANSITION,
        DRAFT_DELETE_ONLY
    }

    public static final class ArticleAdminException extends RuntimeException {

        private final Failure failure;

        private ArticleAdminException(Failure failure) {
            super(switch (failure) {
                case NOT_FOUND -> "Article or taxonomy was not found";
                case DUPLICATE_SLUG -> "Article slug is already in use";
                case STALE_VERSION -> "Article version is stale";
                case INVALID_STATE_TRANSITION -> "Article state transition is not allowed";
                case DRAFT_DELETE_ONLY -> "Only draft articles can be deleted";
            });
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
