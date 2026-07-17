package com.studystack.content.domain;

import com.studystack.shared.slug.Slug;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends Repository<Category, UUID> {

    Category save(Category category);

    Optional<Category> findById(UUID id);

    Optional<Category> findBySlug(Slug slug);

    @Query(value = """
            select c.name as name,
                   c.slug as slug,
                   count(a.id) as "publishedArticleCount"
            from content_category c
            join content_article a on a.category_id = c.id
            where a.status = 'PUBLISHED'
              and a.published_at <= :now
            group by c.id, c.name, c.slug
            order by c.slug asc, c.id asc
            """, nativeQuery = true)
    List<PublicCategoryCount> findPublicCategoryCounts(@Param("now") Instant now);

    interface PublicCategoryCount {

        String getName();

        String getSlug();

        long getPublishedArticleCount();

        default String name() {
            return getName();
        }

        default String slug() {
            return getSlug();
        }

        default long publishedArticleCount() {
            return getPublishedArticleCount();
        }
    }
}
