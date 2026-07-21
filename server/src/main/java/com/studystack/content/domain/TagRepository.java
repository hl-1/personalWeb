package com.studystack.content.domain;

import com.studystack.shared.slug.Slug;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends Repository<Tag, UUID> {

    Tag save(Tag tag);

    Optional<Tag> findById(UUID id);

    Optional<Tag> findBySlug(Slug slug);

    Optional<Tag> findByName(String name);

    List<Tag> findAllByIdIn(Set<UUID> ids);

    void delete(Tag tag);

    void flush();

    @Query(value = """
            select t.id as id,
                   t.name as name,
                   t.slug as slug,
                   count(article_tag.article_id) as "articleCount",
                   t.created_at as "createdAt",
                   t.updated_at as "updatedAt",
                   t.version as version
            from content_tag t
            left join content_article_tag article_tag on article_tag.tag_id = t.id
            group by t.id, t.name, t.slug, t.created_at, t.updated_at, t.version
            order by t.name asc, t.id asc
            """, nativeQuery = true)
    List<AdminTag> findAdminTags();

    @Query("select t from Tag t where t.id = :id")
    Optional<Tag> findByIdForDeletion(@Param("id") UUID id);

    @Query(value = "select count(*) from content_article_tag where tag_id = :id", nativeQuery = true)
    long countArticleReferences(@Param("id") UUID id);

    interface AdminTag {

        UUID getId();

        String getName();

        String getSlug();

        long getArticleCount();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        long getVersion();
    }

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
