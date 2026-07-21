package com.studystack.admin.application;

import com.studystack.admin.domain.AdminAuditAction;
import com.studystack.admin.domain.AdminResourceType;
import com.studystack.content.application.admin.TaxonomyAdminCommand;
import com.studystack.content.application.admin.TaxonomyAdminService;
import com.studystack.content.application.admin.TaxonomyAdminView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminTaxonomyUseCase {

    private final TaxonomyAdminService taxonomy;
    private final AdminAuditService audit;

    AdminTaxonomyUseCase(TaxonomyAdminService taxonomy, AdminAuditService audit) {
        this.taxonomy = Objects.requireNonNull(taxonomy, "taxonomy is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
    }

    @Transactional(readOnly = true)
    public List<TaxonomyAdminView> listCategories() {
        return taxonomy.listCategories();
    }

    @Transactional(readOnly = true)
    public List<TaxonomyAdminView> listTags() {
        return taxonomy.listTags();
    }

    public TaxonomyAdminView createCategory(TaxonomyAdminCommand.Create command) {
        TaxonomyAdminView created = taxonomy.createCategory(command);
        audit.record(AdminAuditAction.CREATE, AdminResourceType.CATEGORY, created.id(), created.version());
        return created;
    }

    public TaxonomyAdminView createTag(TaxonomyAdminCommand.Create command) {
        TaxonomyAdminView created = taxonomy.createTag(command);
        audit.record(AdminAuditAction.CREATE, AdminResourceType.TAG, created.id(), created.version());
        return created;
    }

    public TaxonomyAdminView updateCategory(UUID id, TaxonomyAdminCommand.Update command) {
        TaxonomyAdminView updated = taxonomy.updateCategory(id, command);
        audit.record(AdminAuditAction.UPDATE, AdminResourceType.CATEGORY, updated.id(), updated.version());
        return updated;
    }

    public TaxonomyAdminView updateTag(UUID id, TaxonomyAdminCommand.Update command) {
        TaxonomyAdminView updated = taxonomy.updateTag(id, command);
        audit.record(AdminAuditAction.UPDATE, AdminResourceType.TAG, updated.id(), updated.version());
        return updated;
    }

    public void deleteCategory(UUID id, long version) {
        taxonomy.deleteCategory(id, version);
        audit.record(AdminAuditAction.DELETE, AdminResourceType.CATEGORY, id, null);
    }

    public void deleteTag(UUID id, long version) {
        taxonomy.deleteTag(id, version);
        audit.record(AdminAuditAction.DELETE, AdminResourceType.TAG, id, null);
    }
}
