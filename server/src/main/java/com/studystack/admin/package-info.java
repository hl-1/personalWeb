@ApplicationModule(
        id = "admin",
        displayName = "Admin",
        allowedDependencies = {"comment", "content", "identity", "media", "portfolio", "shared"})
package com.studystack.admin;

import org.springframework.modulith.ApplicationModule;
