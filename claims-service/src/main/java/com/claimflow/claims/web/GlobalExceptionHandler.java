package com.claimflow.claims.web;

import java.util.List;
import java.util.Map;

import com.claimflow.claims.client.EligibilityUnavailableException;
import com.claimflow.claims.domain.InvalidTransitionException;
import com.claimflow.claims.service.ActionNotAllowedException;
import com.claimflow.claims.service.ClaimNotFoundException;
import com.claimflow.claims.service.StaleClaimException;
import com.claimflow.claims.validation.ClaimRejectedException;

import org.springframework.dao.OptimisticLockingFailureException;
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

    @ExceptionHandler(ClaimNotFoundException.class)
    ProblemDetail notFound(ClaimNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ClaimRejectedException.class)
    ProblemDetail rejected(ClaimRejectedException ex) {
        ProblemDetail detail = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Claim rejected",
                "The claim failed " + ex.getViolations().size() + " rule(s)");
        detail.setProperty("violations", ex.getViolations());
        return detail;
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ProblemDetail invalidTransition(InvalidTransitionException ex) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Invalid status transition", ex.getMessage());
        detail.setProperty("currentStatus", ex.getFrom());
        return detail;
    }

    @ExceptionHandler(StaleClaimException.class)
    ResponseEntity<ProblemDetail> stale(StaleClaimException ex) {
        ProblemDetail detail = problem(HttpStatus.PRECONDITION_FAILED, "Claim changed", ex.getMessage());
        detail.setProperty("currentVersion", ex.getCurrentVersion());
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(ETags.of(ex.getCurrentVersion())).body(detail);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update", "Someone else updated this claim at the same moment. Reload and retry.");
    }

    @ExceptionHandler(ETags.PreconditionRequiredException.class)
    ProblemDetail preconditionRequired(ETags.PreconditionRequiredException ex) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, "If-Match required", ex.getMessage());
    }

    @ExceptionHandler(ActionNotAllowedException.class)
    ProblemDetail notAllowed(ActionNotAllowedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Not allowed", ex.getMessage());
    }

    @ExceptionHandler(EligibilityUnavailableException.class)
    ResponseEntity<ProblemDetail> eligibilityDown(EligibilityUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Eligibility unavailable", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
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
