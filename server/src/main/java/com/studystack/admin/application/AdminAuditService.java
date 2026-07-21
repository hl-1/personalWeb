package com.studystack.admin.application;

import com.studystack.admin.domain.AdminAuditAction;
import com.studystack.admin.domain.AdminAuditEntry;
import com.studystack.admin.domain.AdminAuditRepository;
import com.studystack.admin.domain.AdminResourceType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

    private final AdminAuditRepository auditEntries;
    private final AdminActorResolver actorResolver;

    public AdminAuditService(
            AdminAuditRepository auditEntries,
            AdminActorResolver actorResolver) {
        this.auditEntries = Objects.requireNonNull(auditEntries, "auditEntries is required");
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver is required");
    }

    @Transactional
    public void record(
            AdminAuditAction action,
            AdminResourceType resourceType,
            UUID resourceId,
            Long resourceVersion) {
        AdminActor actor = actorResolver.resolve();
        auditEntries.append(new AdminAuditEntry(
                UUID.randomUUID(),
                actor.userId(),
                action,
                resourceType,
                resourceId,
                resourceVersion,
                Instant.now()));
    }
}
