package com.studystack.admin.application;

import com.studystack.admin.domain.AdminAuditAction;
import com.studystack.admin.domain.AdminResourceType;
import com.studystack.portfolio.application.admin.ExperienceAdminService;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Experience;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Profile;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Skill;
import com.studystack.portfolio.application.admin.ProfileAdminService;
import com.studystack.portfolio.application.admin.SkillAdminService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminPortfolioUseCase {

    private static final UUID PROFILE_RESOURCE_ID = new UUID(0, 1);

    private final ProfileAdminService profiles;
    private final SkillAdminService skills;
    private final ExperienceAdminService experiences;
    private final AdminAuditService audit;

    AdminPortfolioUseCase(
            ProfileAdminService profiles,
            SkillAdminService skills,
            ExperienceAdminService experiences,
            AdminAuditService audit) {
        this.profiles = Objects.requireNonNull(profiles, "profiles is required");
        this.skills = Objects.requireNonNull(skills, "skills is required");
        this.experiences = Objects.requireNonNull(experiences, "experiences is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
    }

    @Transactional(readOnly = true)
    public Profile findProfile() {
        return profiles.find();
    }

    public Profile upsertProfile(ProfileAdminService.Command command) {
        Profile profile = profiles.upsert(command);
        AdminAuditAction action = command.version() == null
                ? AdminAuditAction.CREATE
                : AdminAuditAction.UPDATE;
        audit.record(action, AdminResourceType.PROFILE, PROFILE_RESOURCE_ID, profile.version());
        return profile;
    }

    @Transactional(readOnly = true)
    public List<Skill> listSkills() {
        return skills.list();
    }

    public Skill createSkill(SkillAdminService.Create command) {
        Skill skill = skills.create(command);
        audit.record(AdminAuditAction.CREATE, AdminResourceType.SKILL, skill.id(), skill.version());
        return skill;
    }

    public Skill updateSkill(UUID id, SkillAdminService.Update command) {
        Skill skill = skills.update(id, command);
        audit.record(AdminAuditAction.UPDATE, AdminResourceType.SKILL, skill.id(), skill.version());
        return skill;
    }

    public void deleteSkill(UUID id, long version) {
        skills.delete(id, version);
        audit.record(AdminAuditAction.DELETE, AdminResourceType.SKILL, id, null);
    }

    @Transactional(readOnly = true)
    public List<Experience> listExperiences() {
        return experiences.list();
    }

    public Experience createExperience(ExperienceAdminService.Create command) {
        Experience experience = experiences.create(command);
        audit.record(
                AdminAuditAction.CREATE,
                AdminResourceType.EXPERIENCE,
                experience.id(),
                experience.version());
        return experience;
    }

    public Experience updateExperience(UUID id, ExperienceAdminService.Update command) {
        Experience experience = experiences.update(id, command);
        audit.record(
                AdminAuditAction.UPDATE,
                AdminResourceType.EXPERIENCE,
                experience.id(),
                experience.version());
        return experience;
    }

    public void deleteExperience(UUID id, long version) {
        experiences.delete(id, version);
        audit.record(AdminAuditAction.DELETE, AdminResourceType.EXPERIENCE, id, null);
    }
}
