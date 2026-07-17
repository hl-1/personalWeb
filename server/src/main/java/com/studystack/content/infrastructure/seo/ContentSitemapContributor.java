package com.studystack.content.infrastructure.seo;

import com.studystack.content.domain.ArticleRepository;
import com.studystack.shared.seo.SitemapContributor;
import com.studystack.shared.seo.SitemapEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ContentSitemapContributor implements SitemapContributor {

    private static final int DETECTION_LIMIT = 50_001;

    private final ArticleRepository articles;

    public ContentSitemapContributor(ArticleRepository articles) {
        this.articles = articles;
    }

    @Override
    public List<SitemapEntry> entries(Instant now) {
        return articles.findPublicArticles(now, null, null, DETECTION_LIMIT, 0).stream()
                .map(article -> new SitemapEntry(
                        "/blog/" + article.slug().value(), article.updatedAt()))
                .toList();
    }
}
