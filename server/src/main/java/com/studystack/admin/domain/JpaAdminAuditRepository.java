package com.studystack.admin.domain;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
class JpaAdminAuditRepository implements AdminAuditRepository {

    private final EntityManager entityManager;

    JpaAdminAuditRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager is required");
    }

    @Override
    public void append(AdminAuditEntry entry) {
        entityManager.persist(Objects.requireNonNull(entry, "entry is required"));
    }
}
