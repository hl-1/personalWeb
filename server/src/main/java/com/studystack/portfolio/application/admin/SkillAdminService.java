package com.studystack.portfolio.application.admin;

import com.studystack.portfolio.application.admin.PortfolioAdminViews.AdminException;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Failure;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Skill;
import com.studystack.portfolio.domain.SkillRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SkillAdminService {

    private final SkillRepository skills;

    SkillAdminService(SkillRepository skills) {
        this.skills = Objects.requireNonNull(skills, "skills is required");
    }

    @Transactional(readOnly = true)
    public List<Skill> list() {
        return skills.findAdminSkills().stream().map(this::toView).toList();
    }

    public Skill create(Create command) {
        Objects.requireNonNull(command, "command is required");
        com.studystack.portfolio.domain.Skill skill = new com.studystack.portfolio.domain.Skill(
                UUID.randomUUID(),
                command.name(),
                command.category(),
                command.summary(),
                command.sortOrder(),
                command.visible(),
                now());
        return persist(skill);
    }

    public Skill update(UUID id, Update command) {
        Objects.requireNonNull(command, "command is required");
        com.studystack.portfolio.domain.Skill skill = find(id);
        requireVersion(skill.version(), command.version());
        skill.revise(
                command.name(),
                command.category(),
                command.summary(),
                command.sortOrder(),
                command.visible(),
                now());
        return persist(skill);
    }

    public void delete(UUID id, long version) {
        com.studystack.portfolio.domain.Skill skill = find(id);
        requireVersion(skill.version(), version);
        try {
            skills.delete(skill);
            skills.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private com.studystack.portfolio.domain.Skill find(UUID id) {
        Objects.requireNonNull(id, "id is required");
        return skills.findById(id).orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private Skill persist(com.studystack.portfolio.domain.Skill skill) {
        try {
            skills.save(skill);
            skills.flush();
            return toView(skill);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private Skill toView(com.studystack.portfolio.domain.Skill skill) {
        return new Skill(
                skill.id(),
                skill.name(),
                skill.category(),
                skill.summary(),
                skill.sortOrder(),
                skill.visible(),
                skill.createdAt(),
                skill.updatedAt(),
                skill.version());
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
            String name,
            String category,
            String summary,
            int sortOrder,
            boolean visible) {
    }

    public record Update(
            String name,
            String category,
            String summary,
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
