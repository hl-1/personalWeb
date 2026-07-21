package com.studystack.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 16)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 16)
    private AdminResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "resource_version", updatable = false)
    private Long resourceVersion;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AdminAuditEntry() {
    }

    public AdminAuditEntry(
            UUID id,
            UUID actorUserId,
            AdminAuditAction action,
            AdminResourceType resourceType,
            UUID resourceId,
            Long resourceVersion,
            Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId is required");
        this.action = Objects.requireNonNull(action, "action is required");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType is required");
        this.action.requireSupported(this.resourceType);
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        if (resourceVersion != null && resourceVersion < 0) {
            throw new IllegalArgumentException("resourceVersion must be non-negative");
        }
        this.resourceVersion = resourceVersion;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public UUID id() {
        return id;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public AdminAuditAction action() {
        return action;
    }

    public AdminResourceType resourceType() {
        return resourceType;
    }

    public UUID resourceId() {
        return resourceId;
    }

    public Long resourceVersion() {
        return resourceVersion;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "AdminAuditEntry[id=" + id + ", action=" + action + ", resourceType=" + resourceType + "]";
    }
}
