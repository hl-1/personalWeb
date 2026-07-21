package com.studystack.admin.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studystack.content.application.admin.ArticleAdminService;
import com.studystack.content.application.admin.TaxonomyAdminService;
import com.studystack.portfolio.application.admin.ProjectAdminService;
import com.studystack.shared.web.PublicApiExceptionHandler;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
        controllers = AdminErrorContractIntegrationTest.ErrorProbeController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({
        AdminApiExceptionHandler.class,
        PublicApiExceptionHandler.class,
        AdminErrorContractIntegrationTest.ErrorProbeController.class
})
class AdminErrorContractIntegrationTest {

    private static final String SECRET = "SECRET_SQL_AND_MARKDOWN_MARKER";

    @Autowired
    MockMvc mockMvc;

    @Test
    void mapsAllKnownBusinessFailuresToStableSanitizedProblems() throws Exception {
        for (ExpectedFailure expected : List.of(
                new ExpectedFailure(ProbeFailure.NOT_FOUND, 404, "not_found"),
                new ExpectedFailure(ProbeFailure.DUPLICATE_SLUG, 409, "duplicate_slug"),
                new ExpectedFailure(ProbeFailure.STALE_VERSION, 409, "stale_version"),
                new ExpectedFailure(ProbeFailure.INVALID_STATE_TRANSITION, 409, "invalid_state_transition"),
                new ExpectedFailure(ProbeFailure.TAXONOMY_IN_USE, 409, "taxonomy_in_use"),
                new ExpectedFailure(ProbeFailure.DRAFT_DELETE_ONLY, 409, "draft_delete_only"))) {
            expectProblem(
                    "/api/v1/admin/test-errors/" + expected.failure().name(),
                    expected.status(),
                    expected.code());
        }
    }

    @Test
    void mapsIllegalEnumsToValidationFailedInsteadOfInternalErrors() throws Exception {
        expectProblem("/api/v1/admin/test-errors/not-an-enum", 400, "validation_failed");
    }

    @Test
    void sanitizesUnknownFailuresAsInternalErrors() throws Exception {
        expectProblem("/api/v1/admin/test-errors/UNKNOWN", 500, "internal_error");
    }

    private void expectProblem(String path, int expectedStatus, String expectedCode) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.type").value("urn:studystack:problem:" + expectedCode.replace('_', '-')))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").isNotEmpty())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.detail", not(containsString(SECRET))));
    }

    enum ProbeFailure {
        NOT_FOUND,
        DUPLICATE_SLUG,
        STALE_VERSION,
        INVALID_STATE_TRANSITION,
        TAXONOMY_IN_USE,
        DRAFT_DELETE_ONLY,
        UNKNOWN
    }

    @RestController
    @RequestMapping("/api/v1/admin/test-errors")
    static class ErrorProbeController {

        @GetMapping("/{failure}")
        void fail(@PathVariable ProbeFailure failure) {
            throw switch (failure) {
                case NOT_FOUND -> articleFailure(ArticleAdminService.Failure.NOT_FOUND);
                case DUPLICATE_SLUG -> articleFailure(ArticleAdminService.Failure.DUPLICATE_SLUG);
                case STALE_VERSION -> articleFailure(ArticleAdminService.Failure.STALE_VERSION);
                case INVALID_STATE_TRANSITION ->
                        articleFailure(ArticleAdminService.Failure.INVALID_STATE_TRANSITION);
                case TAXONOMY_IN_USE -> taxonomyFailure(TaxonomyAdminService.Failure.TAXONOMY_IN_USE);
                case DRAFT_DELETE_ONLY -> projectFailure(ProjectAdminService.Failure.DRAFT_DELETE_ONLY);
                case UNKNOWN -> new IllegalStateException(SECRET);
            };
        }

        private RuntimeException articleFailure(ArticleAdminService.Failure failure) {
            return instantiate(ArticleAdminService.ArticleAdminException.class, failure);
        }

        private RuntimeException taxonomyFailure(TaxonomyAdminService.Failure failure) {
            return instantiate(TaxonomyAdminService.TaxonomyAdminException.class, failure);
        }

        private RuntimeException projectFailure(ProjectAdminService.Failure failure) {
            return instantiate(ProjectAdminService.ProjectAdminException.class, failure);
        }

        private RuntimeException instantiate(Class<? extends RuntimeException> type, Enum<?> failure) {
            try {
                Constructor<? extends RuntimeException> constructor =
                        type.getDeclaredConstructor(failure.getDeclaringClass());
                constructor.setAccessible(true);
                return constructor.newInstance(failure);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private record ExpectedFailure(ProbeFailure failure, int status, String code) {
    }
}
