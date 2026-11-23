package com.claimflow.claims.domain;

public class InvalidTransitionException extends RuntimeException {

    private final ClaimStatus from;
    private final ClaimStatus to;

    public InvalidTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move a claim from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public ClaimStatus getFrom() {
        return from;
    }

    public ClaimStatus getTo() {
        return to;
    }
}
