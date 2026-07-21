package com.studystack.admin.domain;

public interface AdminAuditRepository {

    void append(AdminAuditEntry entry);
}
