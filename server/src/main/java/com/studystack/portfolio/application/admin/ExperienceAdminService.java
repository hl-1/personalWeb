package com.studystack.portfolio.application.admin;

import com.studystack.portfolio.application.admin.PortfolioAdminViews.AdminException;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Experience;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Failure;
import com.studystack.portfolio.domain.ExperienceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExperienceAdminService {

    private final ExperienceRepository experiences;

    ExperienceAdminService(ExperienceRepository experiences) {
        this.experiences = Objects.requireNonNull(experiences, "experiences is required");
    }

    @Transactional(readOnly = true)
    public List<Experience> list() {
        return experiences.findAdminExperiences().stream().map(this::toView).toList();
    }

    public Experience create(Create command) {
        Objects.requireNonNull(command, "command is required");
        com.studystack.portfolio.domain.Experience experience =
                new com.studystack.portfolio.domain.Experience(
                        UUID.randomUUID(),
                        command.organization(),
                        command.role(),
                        command.startDate(),
                        command.endDate(),
                        command.summaryMarkdown(),
                        command.sortOrder(),
                        command.visible(),
                        now());
        return persist(experience);
    }

    public Experience update(UUID id, Update command) {
        Objects.requireNonNull(command, "command is required");
        com.studystack.portfolio.domain.Experience experience = find(id);
        requireVersion(experience.version(), command.version());
        experience.revise(
                command.organization(),
                command.role(),
                command.startDate(),
                command.endDate(),
                command.summaryMarkdown(),
                command.sortOrder(),
                command.visible(),
                now());
        return persist(experience);
    }

    public void delete(UUID id, long version) {
        com.studystack.portfolio.domain.Experience experience = find(id);
        requireVersion(experience.version(), version);
        try {
            experiences.delete(experience);
            experiences.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private com.studystack.portfolio.domain.Experience find(UUID id) {
        Objects.requireNonNull(id, "id is required");
        return experiences.findById(id).orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private Experience persist(com.studystack.portfolio.domain.Experience experience) {
        try {
            experiences.save(experience);
            experiences.flush();
            return toView(experience);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private Experience toView(com.studystack.portfolio.domain.Experience experience) {
        return new Experience(
                experience.id(),
                experience.organization(),
                experience.role(),
                experience.startDate(),
                experience.endDate(),
                experience.summaryMarkdown(),
                experience.sortOrder(),
                experience.visible(),
                experience.createdAt(),
                experience.updatedAt(),
                experience.version());
    }

    private static void requireVersion(Long actual, long expected) {
        if (expected < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (actual == null || actual != expected) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static AdminException failure(Failure failure) {
        return new AdminException(failure);
    }

    public record Create(
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String summaryMarkdown,
            int sortOrder,
            boolean visible) {
    }

    public record Update(
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String summaryMarkdown,
            int sortOrder,
            boolean visible,
            long version) {

        public Update {
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
        }
    }
}
