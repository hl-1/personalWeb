package com.studystack.content.domain;

import com.studystack.shared.slug.Slug;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends Repository<Article, UUID> {

    Article save(Article article);

    Optional<Article> findById(UUID id);

    Optional<Article> findBySlug(Slug slug);

    @Query(value = """
            select a.*
            from content_article a
            left join content_category c on c.id = a.category_id
            where a.status = 'PUBLISHED'
              and a.published_at <= :now
              and (:categorySlug is null or c.slug = :categorySlug)
              and (:tagSlug is null or exists (
                  select 1
                  from content_article_tag article_tag
                  join content_tag t on t.id = article_tag.tag_id
                  where article_tag.article_id = a.id
                    and t.slug = :tagSlug))
            order by a.published_at desc, a.id desc
            limit :limit offset :offset
            """, nativeQuery = true)
    List<Article> findPublicArticles(
            @Param("now") Instant now,
            @Param("categorySlug") String categorySlug,
            @Param("tagSlug") String tagSlug,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
            select count(*)
            from content_article a
            left join content_category c on c.id = a.category_id
            where a.status = 'PUBLISHED'
              and a.published_at <= :now
              and (:categorySlug is null or c.slug = :categorySlug)
              and (:tagSlug is null or exists (
                  select 1
                  from content_article_tag article_tag
                  join content_tag t on t.id = article_tag.tag_id
                  where article_tag.article_id = a.id
                    and t.slug = :tagSlug))
            """, nativeQuery = true)
    long countPublicArticles(
            @Param("now") Instant now,
            @Param("categorySlug") String categorySlug,
            @Param("tagSlug") String tagSlug);

    @Query(value = """
            select a.*
            from content_article a
            where a.slug = :slug
              and a.status = 'PUBLISHED'
              and a.published_at <= :now
            """, nativeQuery = true)
    Optional<Article> findPublicBySlug(
            @Param("slug") String slug,
            @Param("now") Instant now);

    @Query("""
            select distinct a
            from Article a
            left join fetch a.category
            left join fetch a.tags
            where a.id in :ids
            """)
    List<Article> findAllWithTaxonomyByIdIn(@Param("ids") List<UUID> ids);
}
