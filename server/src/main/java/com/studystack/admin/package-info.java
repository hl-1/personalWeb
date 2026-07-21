@ApplicationModule(
        id = "admin",
        displayName = "Admin",
        allowedDependencies = {
                "content :: admin", "portfolio :: admin", "shared :: markdown", "shared :: web"
        })
package com.studystack.admin;

import org.springframework.modulith.ApplicationModule;
