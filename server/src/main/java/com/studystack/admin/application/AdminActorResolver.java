package com.studystack.admin.application;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class AdminActorResolver {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String DENIED_MESSAGE = "Administrator authentication is required";

    public AdminActor resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()))) {
            throw denied();
        }

        String principalName = authentication.getName();
        if (principalName == null) {
            throw denied();
        }
        try {
            return new AdminActor(UUID.fromString(principalName));
        } catch (IllegalArgumentException exception) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(DENIED_MESSAGE);
    }
}
