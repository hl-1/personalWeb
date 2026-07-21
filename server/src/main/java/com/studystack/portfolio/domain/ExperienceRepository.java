package com.studystack.portfolio.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ExperienceRepository extends Repository<Experience, UUID> {

    Experience save(Experience experience);

    Optional<Experience> findById(UUID id);

    void delete(Experience experience);

    void flush();

    @Query(value = """
            select e.*
            from portfolio_experience e
            order by e.sort_order asc, e.id asc
            """, nativeQuery = true)
    List<Experience> findAdminExperiences();

    @Query(value = """
            select e.*
            from portfolio_experience e
            where e.visible = true
            order by e.sort_order asc, e.start_date desc, e.id asc
            """, nativeQuery = true)
    List<Experience> findVisibleExperiences();
}
