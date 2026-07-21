package com.studystack.support;

import com.studystack.admin.domain.AdminAuditRepository;
import com.studystack.content.application.admin.ArticleAdminService;
import com.studystack.content.application.admin.TaxonomyAdminService;
import com.studystack.portfolio.application.admin.ExperienceAdminService;
import com.studystack.portfolio.application.admin.ProfileAdminService;
import com.studystack.portfolio.application.admin.ProjectAdminService;
import com.studystack.portfolio.application.admin.SkillAdminService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class AdminNoDatabaseTestSupport {

    @MockitoBean protected AdminAuditRepository adminAuditRepository;
    @MockitoBean protected ArticleAdminService articleAdminService;
    @MockitoBean protected TaxonomyAdminService taxonomyAdminService;
    @MockitoBean protected ProjectAdminService projectAdminService;
    @MockitoBean protected ProfileAdminService profileAdminService;
    @MockitoBean protected SkillAdminService skillAdminService;
    @MockitoBean protected ExperienceAdminService experienceAdminService;
}
