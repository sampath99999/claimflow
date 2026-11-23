package com.claimflow.claims.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    DENIED,
    PAID;

    public Set<ClaimStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(IN_REVIEW);
            case IN_REVIEW -> EnumSet.of(APPROVED, DENIED);
            case APPROVED -> EnumSet.of(PAID);
            case DENIED, PAID -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canMoveTo(ClaimStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }
}
