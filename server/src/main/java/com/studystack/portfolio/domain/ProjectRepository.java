package com.studystack.portfolio.domain;

import com.studystack.shared.slug.Slug;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends Repository<Project, UUID> {

    Project save(Project project);

    Optional<Project> findById(UUID id);

    Optional<Project> findBySlug(Slug slug);

    void delete(Project project);

    void flush();

    @Query(value = """
            select p.*
            from portfolio_project p
            where (:status is null or p.status = :status)
              and (:query is null
                   or lower(p.title) like concat('%', lower(:query), '%')
                   or lower(p.slug) like concat('%', lower(:query), '%'))
            order by p.updated_at desc, p.id desc
            limit :limit offset :offset
            """, nativeQuery = true)
    List<Project> findAdminProjects(
            @Param("status") String status,
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
            select count(*)
            from portfolio_project p
            where (:status is null or p.status = :status)
              and (:query is null
                   or lower(p.title) like concat('%', lower(:query), '%')
                   or lower(p.slug) like concat('%', lower(:query), '%'))
            """, nativeQuery = true)
    long countAdminProjects(@Param("status") String status, @Param("query") String query);

    @Query(value = """
            select p.*
            from portfolio_project p
            where p.status = 'PUBLISHED'
              and p.published_at <= :now
              and (:featured is null or p.featured = :featured)
            order by p.published_at desc, p.id desc
            limit :limit offset :offset
            """, nativeQuery = true)
    List<Project> findPublicProjects(
            @Param("now") Instant now,
            @Param("featured") Boolean featured,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
            select count(*)
            from portfolio_project p
            where p.status = 'PUBLISHED'
              and p.published_at <= :now
              and (:featured is null or p.featured = :featured)
            """, nativeQuery = true)
    long countPublicProjects(
            @Param("now") Instant now,
            @Param("featured") Boolean featured);

    @Query(value = """
            select p.*
            from portfolio_project p
            where p.slug = :slug
              and p.status = 'PUBLISHED'
              and p.published_at <= :now
            """, nativeQuery = true)
    Optional<Project> findPublicBySlug(
            @Param("slug") String slug,
            @Param("now") Instant now);
}
