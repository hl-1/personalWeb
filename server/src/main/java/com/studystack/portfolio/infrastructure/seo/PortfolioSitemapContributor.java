package com.studystack.portfolio.infrastructure.seo;

import com.studystack.portfolio.domain.ProjectRepository;
import com.studystack.shared.seo.SitemapContributor;
import com.studystack.shared.seo.SitemapEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class PortfolioSitemapContributor implements SitemapContributor {

    private static final int DETECTION_LIMIT = 50_001;

    private final ProjectRepository projects;

    public PortfolioSitemapContributor(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    public List<SitemapEntry> entries(Instant now) {
        return projects.findPublicProjects(now, null, DETECTION_LIMIT, 0).stream()
                .map(project -> new SitemapEntry(
                        "/projects/" + project.slug().value(), project.updatedAt()))
                .toList();
    }
}
