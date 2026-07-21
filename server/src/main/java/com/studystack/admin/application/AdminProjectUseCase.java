package com.studystack.admin.application;

import com.studystack.admin.domain.AdminAuditAction;
import com.studystack.admin.domain.AdminResourceType;
import com.studystack.portfolio.application.admin.ProjectAdminCommand;
import com.studystack.portfolio.application.admin.ProjectAdminPage;
import com.studystack.portfolio.application.admin.ProjectAdminService;
import com.studystack.portfolio.application.admin.ProjectAdminView;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminProjectUseCase {

    private final ProjectAdminService projects;
    private final AdminAuditService audit;

    AdminProjectUseCase(ProjectAdminService projects, AdminAuditService audit) {
        this.projects = Objects.requireNonNull(projects, "projects is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
    }

    @Transactional(readOnly = true)
    public ProjectAdminPage list(
            int page,
            int size,
            ProjectAdminView.Status status,
            String query) {
        return projects.list(page, size, status, query);
    }

    public ProjectAdminView create(ProjectAdminCommand.Create command) {
        ProjectAdminView created = projects.create(command);
        audit.record(AdminAuditAction.CREATE, AdminResourceType.PROJECT, created.id(), created.version());
        return created;
    }

    @Transactional(readOnly = true)
    public ProjectAdminView find(UUID id) {
        return projects.find(id);
    }

    public ProjectAdminView update(UUID id, ProjectAdminCommand.Update command) {
        ProjectAdminView updated = projects.update(id, command);
        audit.record(AdminAuditAction.UPDATE, AdminResourceType.PROJECT, updated.id(), updated.version());
        return updated;
    }

    public void delete(UUID id, long version) {
        projects.delete(id, version);
        audit.record(AdminAuditAction.DELETE, AdminResourceType.PROJECT, id, null);
    }

    public ProjectAdminView publish(UUID id, long version, Instant publishAt) {
        ProjectAdminView published = projects.publish(id, version, publishAt);
        audit.record(AdminAuditAction.PUBLISH, AdminResourceType.PROJECT, published.id(), published.version());
        return published;
    }

    public ProjectAdminView archive(UUID id, long version) {
        ProjectAdminView archived = projects.archive(id, version);
        audit.record(AdminAuditAction.ARCHIVE, AdminResourceType.PROJECT, archived.id(), archived.version());
        return archived;
    }
}
