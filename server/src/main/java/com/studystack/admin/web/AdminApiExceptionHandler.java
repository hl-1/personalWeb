package com.studystack.admin.web;

import com.studystack.content.application.admin.ArticleAdminService.ArticleAdminException;
import com.studystack.content.application.admin.TaxonomyAdminService.TaxonomyAdminException;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.AdminException;
import com.studystack.portfolio.application.admin.ProjectAdminService.ProjectAdminException;
import com.studystack.shared.web.PublicApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.studystack.admin.web")
public final class AdminApiExceptionHandler {

    @ExceptionHandler(ArticleAdminException.class)
    ResponseEntity<ProblemDetail> handleArticleFailure(
            ArticleAdminException exception,
            HttpServletRequest request) {
        return response(switch (exception.failure()) {
            case NOT_FOUND -> notFound();
            case DUPLICATE_SLUG -> duplicateSlug();
            case STALE_VERSION -> staleVersion();
            case INVALID_STATE_TRANSITION -> invalidStateTransition();
            case DRAFT_DELETE_ONLY -> draftDeleteOnly();
        }, request, null);
    }

    @ExceptionHandler(TaxonomyAdminException.class)
    ResponseEntity<ProblemDetail> handleTaxonomyFailure(
            TaxonomyAdminException exception,
            HttpServletRequest request) {
        return response(switch (exception.failure()) {
            case NOT_FOUND -> notFound();
            case DUPLICATE_SLUG -> duplicateSlug();
            case STALE_VERSION -> staleVersion();
            case TAXONOMY_IN_USE -> new Descriptor(
                    HttpStatus.CONFLICT,
                    "taxonomy_in_use",
                    "Taxonomy in use",
                    "The taxonomy is still referenced by articles");
        }, request, null);
    }

    @ExceptionHandler(ProjectAdminException.class)
    ResponseEntity<ProblemDetail> handleProjectFailure(
            ProjectAdminException exception,
            HttpServletRequest request) {
        return response(switch (exception.failure()) {
            case NOT_FOUND -> notFound();
            case DUPLICATE_SLUG -> duplicateSlug();
            case STALE_VERSION -> staleVersion();
            case INVALID_STATE_TRANSITION -> invalidStateTransition();
            case DRAFT_DELETE_ONLY -> draftDeleteOnly();
        }, request, null);
    }

    @ExceptionHandler(AdminException.class)
    ResponseEntity<ProblemDetail> handlePortfolioFailure(
            AdminException exception,
            HttpServletRequest request) {
        return response(switch (exception.failure()) {
            case NOT_FOUND -> notFound();
            case STALE_VERSION -> staleVersion();
        }, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidBody(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(error.getField(), ignored -> new java.util.ArrayList<>())
                    .add(error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
        }
        return response(validationFailed(), request, fieldErrors);
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            jakarta.validation.ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return response(validationFailed(), request, null);
    }

    private ResponseEntity<ProblemDetail> response(
            Descriptor descriptor,
            HttpServletRequest request,
            Map<String, List<String>> fieldErrors) {
        ProblemDetail problem = PublicApiExceptionHandler.problem(
                descriptor.status(),
                descriptor.code(),
                descriptor.title(),
                descriptor.detail(),
                URI.create(request.getRequestURI()));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return PublicApiExceptionHandler.response(problem);
    }

    private Descriptor validationFailed() {
        return new Descriptor(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Validation failed",
                "One or more request fields are invalid");
    }

    private Descriptor notFound() {
        return new Descriptor(
                HttpStatus.NOT_FOUND, "not_found", "Resource not found", "The requested resource was not found");
    }

    private Descriptor duplicateSlug() {
        return new Descriptor(
                HttpStatus.CONFLICT, "duplicate_slug", "Duplicate slug", "The submitted slug is already in use");
    }

    private Descriptor staleVersion() {
        return new Descriptor(
                HttpStatus.CONFLICT, "stale_version", "Stale version", "The submitted resource version is stale");
    }

    private Descriptor invalidStateTransition() {
        return new Descriptor(
                HttpStatus.CONFLICT,
                "invalid_state_transition",
                "Invalid state transition",
                "The requested state transition is not allowed");
    }

    private Descriptor draftDeleteOnly() {
        return new Descriptor(
                HttpStatus.CONFLICT,
                "draft_delete_only",
                "Draft deletion only",
                "Only draft resources can be deleted");
    }

    private record Descriptor(HttpStatus status, String code, String title, String detail) {
    }
}
