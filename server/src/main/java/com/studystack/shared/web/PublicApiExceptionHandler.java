package com.studystack.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class PublicApiExceptionHandler {

    public static final String INVALID_REQUEST_CODE = "invalid_request";

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> handleErrorResponse(
            ErrorResponseException exception,
            HttpServletRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setInstance(requestInstance(request));
        return ResponseEntity.status(exception.getStatusCode())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return invalidRequest(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleInvalidArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return invalidRequest(request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            ProblemDetail problem = errorResponse.getBody();
            problem.setInstance(requestInstance(request));
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        return response(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "Internal server error",
                "The server could not complete the request",
                requestInstance(request)));
    }

    private ResponseEntity<ProblemDetail> invalidRequest(HttpServletRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                INVALID_REQUEST_CODE,
                "Invalid request",
                "One or more request parameters are invalid",
                requestInstance(request)));
    }

    public static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            URI instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:studystack:problem:" + code.replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(instance);
        problem.setProperty("code", code);
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static URI requestInstance(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
