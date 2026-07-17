@ApplicationModule(
        id = "content",
        displayName = "Content",
        allowedDependencies = {"shared :: markdown", "shared :: seo", "shared :: slug", "shared :: web"})
package com.studystack.content;

import org.springframework.modulith.ApplicationModule;
