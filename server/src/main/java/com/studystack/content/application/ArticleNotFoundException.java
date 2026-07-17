package com.studystack.content.application;

import java.io.Serial;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class ArticleNotFoundException extends ErrorResponseException {

    public static final String CODE = "article_not_found";

    @Serial
    private static final long serialVersionUID = 1L;

    public ArticleNotFoundException() {
        super(HttpStatus.NOT_FOUND, problemDetail(), null);
    }

    public String code() {
        return CODE;
    }

    private static ProblemDetail problemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The requested article is unavailable");
        problem.setType(URI.create("urn:studystack:problem:article-not-found"));
        problem.setTitle("Article not found");
        problem.setProperty("code", CODE);
        return problem;
    }
}
