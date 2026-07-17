package com.studystack.content.domain;

import com.studystack.shared.slug.Slug;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends Repository<Tag, UUID> {

    Tag save(Tag tag);

    Optional<Tag> findById(UUID id);

    Optional<Tag> findBySlug(Slug slug);

    @Query(value = """
            select t.name as name,
                   t.slug as slug,
                   count(a.id) as "publishedArticleCount"
            from content_tag t
            join content_article_tag article_tag on article_tag.tag_id = t.id
            join content_article a on a.id = article_tag.article_id
            where a.status = 'PUBLISHED'
              and a.published_at <= :now
            group by t.id, t.name, t.slug
            order by t.slug asc, t.id asc
            """, nativeQuery = true)
    List<PublicTagCount> findPublicTagCounts(@Param("now") Instant now);

    interface PublicTagCount {

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
