package com.studystack.portfolio.domain;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface PortfolioProfileRepository extends Repository<PortfolioProfile, Integer> {

    PortfolioProfile save(PortfolioProfile profile);

    Optional<PortfolioProfile> findById(Integer id);

    void flush();
}
