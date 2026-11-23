package com.claimflow.member.web;

import java.util.List;
import java.util.Map;

import com.claimflow.member.service.ConflictException;
import com.claimflow.member.service.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update", "The record was changed by someone else. Reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "The change violates a data constraint");
    }

    @ExceptionHandler({IllegalArgumentException.class, PropertyReferenceException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
