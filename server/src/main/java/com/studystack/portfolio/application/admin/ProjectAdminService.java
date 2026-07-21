package com.studystack.portfolio.application.admin;

import com.studystack.portfolio.domain.Project;
import com.studystack.portfolio.domain.ProjectRepository;
import com.studystack.shared.slug.Slug;
import com.studystack.shared.slug.SlugPolicy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectAdminService {

    private static final int MAXIMUM_PAGE_SIZE = 100;
    private static final int MAXIMUM_QUERY_LENGTH = 100;
    private static final String PROJECT_SLUG_CONSTRAINT = "uk_portfolio_project_slug";

    private final ProjectRepository projects;
    private final SlugPolicy slugPolicy;

    ProjectAdminService(ProjectRepository projects, SlugPolicy slugPolicy) {
        this.projects = Objects.requireNonNull(projects, "projects is required");
        this.slugPolicy = Objects.requireNonNull(slugPolicy, "slugPolicy is required");
    }

    @Transactional(readOnly = true)
    public ProjectAdminPage list(int page, int size, ProjectAdminView.Status status, String query) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        String normalizedQuery = normalizeQuery(query);
        String statusValue = status == null ? null : status.name();
        long totalElements = projects.countAdminProjects(statusValue, normalizedQuery);
        List<ProjectAdminView.Summary> content = projects.findAdminProjects(
                        statusValue, normalizedQuery, size, (long) page * size).stream()
                .map(this::toSummary)
                .toList();
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new ProjectAdminPage(content, page, size, totalElements, totalPages);
    }

    public ProjectAdminView create(ProjectAdminCommand.Create command) {
        Objects.requireNonNull(command, "command is required");
        Slug slug = slugPolicy.create(command.slug());
        requireUniqueSlug(slug, null);
        Instant timestamp = now();
        Project project = new Project(
                UUID.randomUUID(),
                slug,
                command.title(),
                command.summary(),
                command.descriptionMarkdown(),
                command.projectUrl(),
                command.repositoryUrl(),
                command.featured(),
                command.sortOrder(),
                timestamp);
        return persist(project);
    }

    @Transactional(readOnly = true)
    public ProjectAdminView find(UUID id) {
        return toView(findProject(id));
    }

    public ProjectAdminView update(UUID id, ProjectAdminCommand.Update command) {
        Objects.requireNonNull(command, "command is required");
        Project project = findProject(id);
        requireVersion(project, command.version());
        try {
            project.requireEditable();
            Slug requestedSlug = slugPolicy.create(command.slug());
            requireUniqueSlug(requestedSlug, project.id());
            Instant timestamp = now();
            project.changeSlug(requestedSlug.value(), slugPolicy, timestamp);
            project.revise(
                    command.title(),
                    command.summary(),
                    command.descriptionMarkdown(),
                    command.projectUrl(),
                    command.repositoryUrl(),
                    command.featured(),
                    command.sortOrder(),
                    timestamp);
            return persist(project);
        } catch (IllegalStateException exception) {
            throw failure(Failure.INVALID_STATE_TRANSITION);
        }
    }

    public void delete(UUID id, long version) {
        Project project = findProject(id);
        requireVersion(project, version);
        try {
            project.requireDraftDeletion();
        } catch (IllegalStateException exception) {
            throw failure(Failure.DRAFT_DELETE_ONLY);
        }
        try {
            projects.delete(project);
            projects.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    public ProjectAdminView publish(UUID id, long version, Instant publishAt) {
        Project project = findProject(id);
        requireVersion(project, version);
        Instant timestamp = now();
        Instant publicationTime = publishAt == null
                ? timestamp
                : publishAt.truncatedTo(ChronoUnit.MICROS);
        try {
            project.publish(publicationTime, timestamp);
            return persist(project);
        } catch (IllegalStateException exception) {
            throw failure(Failure.INVALID_STATE_TRANSITION);
        }
    }

    public ProjectAdminView archive(UUID id, long version) {
        Project project = findProject(id);
        requireVersion(project, version);
        try {
            project.archive(now());
            return persist(project);
        } catch (IllegalStateException exception) {
            throw failure(Failure.INVALID_STATE_TRANSITION);
        }
    }

    private ProjectAdminView persist(Project project) {
        try {
            projects.save(project);
            projects.flush();
            return toView(project);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, PROJECT_SLUG_CONSTRAINT)) {
                throw failure(Failure.DUPLICATE_SLUG);
            }
            throw exception;
        }
    }

    private Project findProject(UUID id) {
        Objects.requireNonNull(id, "id is required");
        return projects.findById(id).orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private void requireVersion(Project project, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (project.version() == null || project.version() != expectedVersion) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private void requireUniqueSlug(Slug slug, UUID currentProjectId) {
        projects.findBySlug(slug).ifPresent(existing -> {
            if (!existing.id().equals(currentProjectId)) {
                throw failure(Failure.DUPLICATE_SLUG);
            }
        });
    }

    private ProjectAdminView toView(Project project) {
        return new ProjectAdminView(
                project.id(),
                project.slug().value(),
                project.title(),
                project.summary(),
                project.descriptionMarkdown(),
                project.projectUrl(),
                project.repositoryUrl(),
                ProjectAdminView.Status.valueOf(project.status().name()),
                project.featured(),
                project.sortOrder(),
                project.publishedAt(),
                project.createdAt(),
                project.updatedAt(),
                project.version());
    }

    private ProjectAdminView.Summary toSummary(Project project) {
        return new ProjectAdminView.Summary(
                project.id(),
                project.slug().value(),
                project.title(),
                project.summary(),
                ProjectAdminView.Status.valueOf(project.status().name()),
                project.featured(),
                project.sortOrder(),
                project.publishedAt(),
                project.updatedAt(),
                project.version());
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

    private static ProjectAdminException failure(Failure failure) {
        return new ProjectAdminException(failure);
    }

    public enum Failure {
        NOT_FOUND,
        DUPLICATE_SLUG,
        STALE_VERSION,
        INVALID_STATE_TRANSITION,
        DRAFT_DELETE_ONLY
    }

    public static final class ProjectAdminException extends RuntimeException {

        private final Failure failure;

        private ProjectAdminException(Failure failure) {
            super(switch (failure) {
                case NOT_FOUND -> "Project was not found";
                case DUPLICATE_SLUG -> "Project slug is already in use";
                case STALE_VERSION -> "Project version is stale";
                case INVALID_STATE_TRANSITION -> "Project state transition is not allowed";
                case DRAFT_DELETE_ONLY -> "Only draft projects can be deleted";
            });
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
