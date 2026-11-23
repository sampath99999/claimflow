package com.claimflow.claims.validation;

import java.util.List;

public class ClaimRejectedException extends RuntimeException {

    private final List<Violation> violations;

    public ClaimRejectedException(List<Violation> violations) {
        super("Claim rejected: " + violations.stream().map(Violation::code).toList());
        this.violations = List.copyOf(violations);
    }

    public ClaimRejectedException(Violation violation) {
        this(List.of(violation));
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
