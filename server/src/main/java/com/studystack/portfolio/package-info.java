@ApplicationModule(
        id = "portfolio",
        displayName = "Portfolio",
        allowedDependencies = {"shared :: markdown", "shared :: seo", "shared :: slug", "shared :: web"})
package com.studystack.portfolio;

import org.springframework.modulith.ApplicationModule;
