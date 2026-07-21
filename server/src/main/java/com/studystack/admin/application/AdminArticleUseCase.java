package com.studystack.admin.application;

import com.studystack.admin.domain.AdminAuditAction;
import com.studystack.admin.domain.AdminResourceType;
import com.studystack.content.application.admin.ArticleAdminCommand;
import com.studystack.content.application.admin.ArticleAdminPage;
import com.studystack.content.application.admin.ArticleAdminService;
import com.studystack.content.application.admin.ArticleAdminView;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminArticleUseCase {

    private final ArticleAdminService articles;
    private final AdminAuditService audit;

    AdminArticleUseCase(ArticleAdminService articles, AdminAuditService audit) {
        this.articles = Objects.requireNonNull(articles, "articles is required");
        this.audit = Objects.requireNonNull(audit, "audit is required");
    }

    @Transactional(readOnly = true)
    public ArticleAdminPage list(
            int page,
            int size,
            ArticleAdminView.Status status,
            String query) {
        return articles.list(page, size, status, query);
    }

    public ArticleAdminView create(ArticleAdminCommand.Create command) {
        ArticleAdminView created = articles.create(command);
        audit.record(
                AdminAuditAction.CREATE,
                AdminResourceType.ARTICLE,
                created.id(),
                created.version());
        return created;
    }

    @Transactional(readOnly = true)
    public ArticleAdminView find(UUID id) {
        return articles.find(id);
    }

    public ArticleAdminView update(UUID id, ArticleAdminCommand.Update command) {
        ArticleAdminView updated = articles.update(id, command);
        audit.record(
                AdminAuditAction.UPDATE,
                AdminResourceType.ARTICLE,
                updated.id(),
                updated.version());
        return updated;
    }

    public void delete(UUID id, long version) {
        articles.delete(id, version);
        audit.record(AdminAuditAction.DELETE, AdminResourceType.ARTICLE, id, null);
    }

    public ArticleAdminView publish(UUID id, long version, Instant publishAt) {
        ArticleAdminView published = articles.publish(id, version, publishAt);
        audit.record(
                AdminAuditAction.PUBLISH,
                AdminResourceType.ARTICLE,
                published.id(),
                published.version());
        return published;
    }

    public ArticleAdminView archive(UUID id, long version) {
        ArticleAdminView archived = articles.archive(id, version);
        audit.record(
                AdminAuditAction.ARCHIVE,
                AdminResourceType.ARTICLE,
                archived.id(),
                archived.version());
        return archived;
    }
}
