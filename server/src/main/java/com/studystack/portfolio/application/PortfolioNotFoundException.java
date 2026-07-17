package com.studystack.portfolio.application;

import java.io.Serial;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class PortfolioNotFoundException extends ErrorResponseException {

    public static final String CODE = "portfolio_not_found";

    @Serial
    private static final long serialVersionUID = 1L;

    public PortfolioNotFoundException() {
        super(HttpStatus.NOT_FOUND, problemDetail(), null);
    }

    public String code() {
        return CODE;
    }

    private static ProblemDetail problemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The requested portfolio resource is unavailable");
        problem.setType(URI.create("urn:studystack:problem:portfolio-not-found"));
        problem.setTitle("Portfolio resource not found");
        problem.setProperty("code", CODE);
        return problem;
    }
}
