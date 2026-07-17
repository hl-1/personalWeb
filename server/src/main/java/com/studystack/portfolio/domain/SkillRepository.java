package com.studystack.portfolio.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface SkillRepository extends Repository<Skill, UUID> {

    Skill save(Skill skill);

    Optional<Skill> findById(UUID id);

    @Query(value = """
            select s.*
            from portfolio_skill s
            where s.visible = true
            order by s.sort_order asc, s.id asc
            """, nativeQuery = true)
    List<Skill> findVisibleSkills();
}
