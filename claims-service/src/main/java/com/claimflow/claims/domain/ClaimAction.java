package com.claimflow.claims.domain;

public enum ClaimAction {
    START_REVIEW(ClaimStatus.IN_REVIEW),
    APPROVE(ClaimStatus.APPROVED),
    DENY(ClaimStatus.DENIED),
    MARK_PAID(ClaimStatus.PAID);

    private final ClaimStatus target;

    ClaimAction(ClaimStatus target) {
        this.target = target;
    }

    public ClaimStatus target() {
        return target;
    }
}
