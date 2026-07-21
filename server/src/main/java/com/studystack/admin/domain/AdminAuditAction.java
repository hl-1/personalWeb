package com.studystack.admin.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum AdminAuditAction {
    CREATE(EnumSet.allOf(AdminResourceType.class)),
    UPDATE(EnumSet.allOf(AdminResourceType.class)),
    DELETE(EnumSet.of(
            AdminResourceType.ARTICLE,
            AdminResourceType.CATEGORY,
            AdminResourceType.TAG,
            AdminResourceType.PROJECT,
            AdminResourceType.SKILL,
            AdminResourceType.EXPERIENCE)),
    PUBLISH(EnumSet.of(AdminResourceType.ARTICLE, AdminResourceType.PROJECT)),
    ARCHIVE(EnumSet.of(AdminResourceType.ARTICLE, AdminResourceType.PROJECT));

    private final Set<AdminResourceType> supportedResourceTypes;

    AdminAuditAction(Set<AdminResourceType> supportedResourceTypes) {
        this.supportedResourceTypes = Set.copyOf(supportedResourceTypes);
    }

    public boolean supports(AdminResourceType resourceType) {
        return supportedResourceTypes.contains(Objects.requireNonNull(
                resourceType, "resourceType is required"));
    }

    void requireSupported(AdminResourceType resourceType) {
        if (!supports(resourceType)) {
            throw new IllegalArgumentException(
                    name() + " is not supported for " + resourceType.name());
        }
    }
}
