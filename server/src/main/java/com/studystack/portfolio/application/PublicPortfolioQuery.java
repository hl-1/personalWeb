package com.studystack.portfolio.application;

import com.studystack.portfolio.domain.ExperienceRepository;
import com.studystack.portfolio.domain.PortfolioProfile;
import com.studystack.portfolio.domain.PortfolioProfileRepository;
import com.studystack.portfolio.domain.Project;
import com.studystack.portfolio.domain.ProjectRepository;
import com.studystack.portfolio.domain.SkillRepository;
import com.studystack.shared.markdown.MarkdownRenderer;
import com.studystack.shared.slug.SlugPolicy;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicPortfolioQuery {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAXIMUM_SIZE = 50;

    private final PortfolioProfileRepository profiles;
    private final ProjectRepository projects;
    private final SkillRepository skills;
    private final ExperienceRepository experiences;
    private final SlugPolicy slugPolicy;
    private final MarkdownRenderer markdownRenderer;

    public PublicPortfolioQuery(
            PortfolioProfileRepository profiles,
            ProjectRepository projects,
            SkillRepository skills,
            ExperienceRepository experiences,
            SlugPolicy slugPolicy,
            MarkdownRenderer markdownRenderer) {
        this.profiles = profiles;
        this.projects = projects;
        this.skills = skills;
        this.experiences = experiences;
        this.slugPolicy = slugPolicy;
        this.markdownRenderer = markdownRenderer;
    }

    public PortfolioProfileView findProfile() {
        PortfolioProfile profile = profiles.findById(PortfolioProfile.SINGLETON_ID)
                .orElseThrow(PortfolioNotFoundException::new);
        return new PortfolioProfileView(
                profile.displayName(),
                profile.headline(),
                markdownRenderer.render(profile.bioMarkdown()).html(),
                profile.seoDescription());
    }

    public Page<ProjectSummary> findProjects(
            Integer requestedPage,
            Integer requestedSize,
            Boolean featured) {
        int page = validatePage(requestedPage);
        int size = validateSize(requestedSize);
        Instant now = Instant.now();
        List<ProjectSummary> summaries = projects.findPublicProjects(
                        now, featured, size, (long) page * size)
                .stream()
                .map(this::toSummary)
                .toList();
        long totalElements = projects.countPublicProjects(now, featured);
        return new PageImpl<>(summaries, PageRequest.of(page, size), totalElements);
    }

    public ProjectDetail findProject(String requestedSlug) {
        String slug = normalizeDetailSlug(requestedSlug);
        Project project = projects.findPublicBySlug(slug, Instant.now())
                .orElseThrow(PortfolioNotFoundException::new);
        ProjectSummary summary = toSummary(project);
        return new ProjectDetail(
                summary.id(),
                summary.slug(),
                summary.title(),
                summary.summary(),
                summary.featured(),
                summary.publishedAt(),
                summary.updatedAt(),
                markdownRenderer.render(project.descriptionMarkdown()).html(),
                project.projectUrl(),
                project.repositoryUrl(),
                "/projects/" + summary.slug());
    }

    public List<SkillView> findSkills() {
        return skills.findVisibleSkills().stream()
                .map(skill -> new SkillView(
                        skill.id(), skill.name(), skill.category(), skill.summary()))
                .toList();
    }

    public List<ExperienceView> findExperiences() {
        return experiences.findVisibleExperiences().stream()
                .map(experience -> new ExperienceView(
                        experience.id(),
                        experience.organization(),
                        experience.role(),
                        experience.startDate(),
                        experience.endDate(),
                        markdownRenderer.render(experience.summaryMarkdown()).html()))
                .toList();
    }

    private ProjectSummary toSummary(Project project) {
        return new ProjectSummary(
                project.id(),
                project.slug().value(),
                project.title(),
                project.summary(),
                project.featured(),
                project.publishedAt(),
                project.updatedAt());
    }

    private int validatePage(Integer requestedPage) {
        int page = requestedPage == null ? DEFAULT_PAGE : requestedPage;
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        return page;
    }

    private int validateSize(Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_SIZE : requestedSize;
        if (size < 1 || size > MAXIMUM_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 50");
        }
        return size;
    }

    private String normalizeDetailSlug(String value) {
        try {
            return slugPolicy.create(value).value();
        } catch (IllegalArgumentException exception) {
            throw new PortfolioNotFoundException();
        }
    }
}
