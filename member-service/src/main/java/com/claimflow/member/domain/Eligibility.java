package com.claimflow.member.domain;

public record Eligibility(boolean eligible, Reason reason) {

    public enum Reason {
        ELIGIBLE,
        MEMBER_NOT_ACTIVE,
        OUTSIDE_COVERAGE_PERIOD,
        PLAN_INACTIVE
    }

    public static Eligibility covered() {
        return new Eligibility(true, Reason.ELIGIBLE);
    }

    public static Eligibility notCovered(Reason reason) {
        return new Eligibility(false, reason);
    }
}
