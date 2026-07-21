package com.studystack.content.application.admin;

import com.studystack.content.domain.Category;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.Tag;
import com.studystack.content.domain.TagRepository;
import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxonomyAdminService {

    private static final String CATEGORY_NAME_CONSTRAINT = "uk_content_category_name";
    private static final String CATEGORY_SLUG_CONSTRAINT = "uk_content_category_slug";
    private static final String TAG_NAME_CONSTRAINT = "uk_content_tag_name";
    private static final String TAG_SLUG_CONSTRAINT = "uk_content_tag_slug";

    private final CategoryRepository categories;
    private final TagRepository tags;
    private final SlugPolicy slugPolicy;

    TaxonomyAdminService(
            CategoryRepository categories,
            TagRepository tags,
            SlugPolicy slugPolicy) {
        this.categories = Objects.requireNonNull(categories, "categories is required");
        this.tags = Objects.requireNonNull(tags, "tags is required");
        this.slugPolicy = Objects.requireNonNull(slugPolicy, "slugPolicy is required");
    }

    @Transactional(readOnly = true)
    public List<TaxonomyAdminView> listCategories() {
        return categories.findAdminCategories().stream()
                .map(category -> new TaxonomyAdminView(
                        category.getId(),
                        category.getName(),
                        category.getSlug(),
                        category.getArticleCount(),
                        category.getCreatedAt(),
                        category.getUpdatedAt(),
                        category.getVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxonomyAdminView> listTags() {
        return tags.findAdminTags().stream()
                .map(tag -> new TaxonomyAdminView(
                        tag.getId(),
                        tag.getName(),
                        tag.getSlug(),
                        tag.getArticleCount(),
                        tag.getCreatedAt(),
                        tag.getUpdatedAt(),
                        tag.getVersion()))
                .toList();
    }

    public TaxonomyAdminView createCategory(TaxonomyAdminCommand.Create command) {
        Objects.requireNonNull(command, "command is required");
        Slug slug = slugPolicy.create(command.slug());
        requireUniqueCategory(command.name(), slug, null);
        Instant timestamp = now();
        Category category = new Category(UUID.randomUUID(), command.name(), slug, timestamp);
        return persistCategory(category);
    }

    public TaxonomyAdminView createTag(TaxonomyAdminCommand.Create command) {
        Objects.requireNonNull(command, "command is required");
        Slug slug = slugPolicy.create(command.slug());
        requireUniqueTag(command.name(), slug, null);
        Instant timestamp = now();
        Tag tag = new Tag(UUID.randomUUID(), command.name(), slug, timestamp);
        return persistTag(tag);
    }

    public TaxonomyAdminView updateCategory(UUID id, TaxonomyAdminCommand.Update command) {
        Objects.requireNonNull(command, "command is required");
        Category category = requireCategory(id);
        requireVersion(category.version(), command.version());
        Slug slug = slugPolicy.create(command.slug());
        requireUniqueCategory(command.name(), slug, category.id());
        category.update(command.name(), slug, now());
        return persistCategory(category);
    }

    public TaxonomyAdminView updateTag(UUID id, TaxonomyAdminCommand.Update command) {
        Objects.requireNonNull(command, "command is required");
        Tag tag = requireTag(id);
        requireVersion(tag.version(), command.version());
        Slug slug = slugPolicy.create(command.slug());
        requireUniqueTag(command.name(), slug, tag.id());
        tag.update(command.name(), slug, now());
        return persistTag(tag);
    }

    public void deleteCategory(UUID id, long version) {
        Category category = categories.findByIdForDeletion(Objects.requireNonNull(id, "id is required"))
                .orElseThrow(() -> failure(Failure.NOT_FOUND));
        requireVersion(category.version(), version);
        if (categories.countArticleReferences(category.id()) != 0) {
            throw failure(Failure.TAXONOMY_IN_USE);
        }
        try {
            categories.delete(category);
            categories.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    public void deleteTag(UUID id, long version) {
        Tag tag = tags.findByIdForDeletion(Objects.requireNonNull(id, "id is required"))
                .orElseThrow(() -> failure(Failure.NOT_FOUND));
        requireVersion(tag.version(), version);
        if (tags.countArticleReferences(tag.id()) != 0) {
            throw failure(Failure.TAXONOMY_IN_USE);
        }
        try {
            tags.delete(tag);
            tags.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private TaxonomyAdminView persistCategory(Category category) {
        try {
            categories.save(category);
            categories.flush();
            return toView(category, categories.countArticleReferences(category.id()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, CATEGORY_NAME_CONSTRAINT)
                    || hasConstraint(exception, CATEGORY_SLUG_CONSTRAINT)) {
                throw failure(Failure.DUPLICATE_SLUG);
            }
            throw exception;
        }
    }

    private TaxonomyAdminView persistTag(Tag tag) {
        try {
            tags.save(tag);
            tags.flush();
            return toView(tag, tags.countArticleReferences(tag.id()));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, TAG_NAME_CONSTRAINT)
                    || hasConstraint(exception, TAG_SLUG_CONSTRAINT)) {
                throw failure(Failure.DUPLICATE_SLUG);
            }
            throw exception;
        }
    }

    private Category requireCategory(UUID id) {
        return categories.findById(Objects.requireNonNull(id, "id is required"))
                .orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private Tag requireTag(UUID id) {
        return tags.findById(Objects.requireNonNull(id, "id is required"))
                .orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private void requireUniqueCategory(String name, Slug slug, UUID currentId) {
        categories.findByName(name).ifPresent(existing -> requireSameId(existing.id(), currentId));
        categories.findBySlug(slug).ifPresent(existing -> requireSameId(existing.id(), currentId));
    }

    private void requireUniqueTag(String name, Slug slug, UUID currentId) {
        tags.findByName(name).ifPresent(existing -> requireSameId(existing.id(), currentId));
        tags.findBySlug(slug).ifPresent(existing -> requireSameId(existing.id(), currentId));
    }

    private static void requireSameId(UUID existingId, UUID currentId) {
        if (!existingId.equals(currentId)) {
            throw failure(Failure.DUPLICATE_SLUG);
        }
    }

    private static void requireVersion(Long actualVersion, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (actualVersion == null || actualVersion != expectedVersion) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private static TaxonomyAdminView toView(Category category, long articleCount) {
        return new TaxonomyAdminView(
                category.id(),
                category.name(),
                category.slug().value(),
                articleCount,
                category.createdAt(),
                category.updatedAt(),
                category.version());
    }

    private static TaxonomyAdminView toView(Tag tag, long articleCount) {
        return new TaxonomyAdminView(
                tag.id(),
                tag.name(),
                tag.slug().value(),
                articleCount,
                tag.createdAt(),
                tag.updatedAt(),
                tag.version());
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

    private static TaxonomyAdminException failure(Failure failure) {
        return new TaxonomyAdminException(failure);
    }

    public enum Failure {
        NOT_FOUND,
        DUPLICATE_SLUG,
        STALE_VERSION,
        TAXONOMY_IN_USE
    }

    public static final class TaxonomyAdminException extends RuntimeException {

        private final Failure failure;

        private TaxonomyAdminException(Failure failure) {
            super(switch (failure) {
                case NOT_FOUND -> "Taxonomy was not found";
                case DUPLICATE_SLUG -> "Taxonomy name or slug is already in use";
                case STALE_VERSION -> "Taxonomy version is stale";
                case TAXONOMY_IN_USE -> "Taxonomy is still referenced by articles";
            });
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
