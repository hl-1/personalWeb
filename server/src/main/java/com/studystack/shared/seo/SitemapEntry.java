package com.studystack.shared.seo;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

@NamedInterface("seo")
public record SitemapEntry(String path, Instant lastModified) {

    public SitemapEntry {
        Objects.requireNonNull(path, "path is required");
        URI uri;
        try {
            uri = URI.create(path);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("path must be a site-relative absolute path", exception);
        }
        if (!path.startsWith("/")
                || path.startsWith("//")
                || uri.isAbsolute()
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("path must be a site-relative absolute path");
        }
    }
}
