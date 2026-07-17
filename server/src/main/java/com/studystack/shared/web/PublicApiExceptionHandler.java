package com.studystack.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    private ResponseEntity<ProblemDetail> invalidRequest(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request parameters are invalid");
        problem.setType(URI.create("urn:studystack:problem:invalid-request"));
        problem.setTitle("Invalid request");
        problem.setInstance(requestInstance(request));
        problem.setProperty("code", INVALID_REQUEST_CODE);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private URI requestInstance(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
