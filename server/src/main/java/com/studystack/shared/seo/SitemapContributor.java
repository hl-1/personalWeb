package com.studystack.shared.seo;

import java.time.Instant;
import java.util.List;
import org.springframework.modulith.NamedInterface;

@FunctionalInterface
@NamedInterface("seo")
public interface SitemapContributor {

    List<SitemapEntry> entries(Instant now);
}
