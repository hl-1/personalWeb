package com.studystack.content.application;

import com.studystack.content.domain.Article;
import com.studystack.content.domain.ArticleRepository;
import com.studystack.content.domain.CategoryRepository;
import com.studystack.content.domain.TagRepository;
import com.studystack.shared.markdown.MarkdownRenderer;
import com.studystack.shared.slug.SlugPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicArticleQuery {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAXIMUM_SIZE = 50;

    private final ArticleRepository articles;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final SlugPolicy slugPolicy;
    private final MarkdownRenderer markdownRenderer;

    public PublicArticleQuery(
            ArticleRepository articles,
            CategoryRepository categories,
            TagRepository tags,
            SlugPolicy slugPolicy,
            MarkdownRenderer markdownRenderer) {
        this.articles = articles;
        this.categories = categories;
        this.tags = tags;
        this.slugPolicy = slugPolicy;
        this.markdownRenderer = markdownRenderer;
    }

    public Page<ArticleSummary> findArticles(
            Integer requestedPage,
            Integer requestedSize,
            String categorySlug,
            String tagSlug) {
        int page = validatePage(requestedPage);
        int size = validateSize(requestedSize);
        String normalizedCategory = normalizeOptionalSlug(categorySlug);
        String normalizedTag = normalizeOptionalSlug(tagSlug);
        Instant now = Instant.now();
        long offset = (long) page * size;

        List<Article> pageArticles = articles.findPublicArticles(
                now, normalizedCategory, normalizedTag, size, offset);
        Map<UUID, Article> articlesWithTaxonomy = loadTaxonomy(pageArticles);
        List<ArticleSummary> summaries = pageArticles.stream()
                .map(article -> articlesWithTaxonomy.getOrDefault(article.id(), article))
                .map(this::toSummary)
                .toList();
        long totalElements = articles.countPublicArticles(now, normalizedCategory, normalizedTag);

        return new PageImpl<>(summaries, PageRequest.of(page, size), totalElements);
    }

    public ArticleDetail findArticle(String requestedSlug) {
        String slug = normalizeDetailSlug(requestedSlug);
        Article article = articles.findPublicBySlug(slug, Instant.now())
                .orElseThrow(ArticleNotFoundException::new);
        Article loadedArticle = loadTaxonomy(List.of(article)).getOrDefault(article.id(), article);
        ArticleSummary summary = toSummary(loadedArticle);

        return new ArticleDetail(
                summary.id(),
                summary.slug(),
                summary.title(),
                summary.summary(),
                summary.category(),
                summary.tags(),
                summary.publishedAt(),
                summary.updatedAt(),
                markdownRenderer.render(loadedArticle.bodyMarkdown()).html(),
                loadedArticle.seoTitle(),
                loadedArticle.seoDescription(),
                "/blog/" + summary.slug());
    }

    public List<TaxonomySummary> findCategories() {
        Instant now = Instant.now();
        return categories.findPublicCategoryCounts(now).stream()
                .map(count -> new TaxonomySummary(
                        count.name(), count.slug(), count.publishedArticleCount()))
                .toList();
    }

    public List<TaxonomySummary> findTags() {
        Instant now = Instant.now();
        return tags.findPublicTagCounts(now).stream()
                .map(count -> new TaxonomySummary(
                        count.name(), count.slug(), count.publishedArticleCount()))
                .toList();
    }

    private Map<UUID, Article> loadTaxonomy(List<Article> pageArticles) {
        if (pageArticles.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = pageArticles.stream().map(Article::id).toList();
        return articles.findAllWithTaxonomyByIdIn(ids).stream()
                .collect(Collectors.toMap(Article::id, Function.identity()));
    }

    private ArticleSummary toSummary(Article article) {
        String category = article.category() == null
                ? null
                : article.category().slug().value();
        List<String> tagSlugs = article.tags().stream()
                .map(tag -> tag.slug().value())
                .sorted()
                .toList();
        return new ArticleSummary(
                article.id(),
                article.slug().value(),
                article.title(),
                article.summary(),
                category,
                tagSlugs,
                article.publishedAt(),
                article.updatedAt());
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

    private String normalizeOptionalSlug(String value) {
        return value == null ? null : slugPolicy.create(value).value();
    }

    private String normalizeDetailSlug(String value) {
        try {
            return slugPolicy.create(value).value();
        } catch (IllegalArgumentException exception) {
            throw new ArticleNotFoundException();
        }
    }
}
