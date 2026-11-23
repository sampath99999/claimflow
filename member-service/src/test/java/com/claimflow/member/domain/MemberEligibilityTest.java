package com.claimflow.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MemberEligibilityTest {

    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 12, 31);

    private final Plan plan = new Plan("GOLD-PPO", "Gold", PlanType.PPO, new BigDecimal("1000.00"), 20, new BigDecimal("6000.00"));

    @ParameterizedTest
    @CsvSource({
            "2025-01-01, true, ELIGIBLE",
            "2025-12-31, true, ELIGIBLE",
            "2024-12-31, false, OUTSIDE_COVERAGE_PERIOD",
            "2026-01-01, false, OUTSIDE_COVERAGE_PERIOD"
    })
    void coverageWindowIsInclusive(LocalDate serviceDate, boolean eligible, Eligibility.Reason reason) {
        Member member = member(END);

        Eligibility result = member.eligibilityOn(serviceDate);

        assertThat(result.eligible()).isEqualTo(eligible);
        assertThat(result.reason()).isEqualTo(reason);
    }

    @Test
    void openEndedCoverageStaysEligible() {
        assertThat(member(null).eligibilityOn(LocalDate.of(2030, 6, 1)).eligible()).isTrue();
    }

    @Test
    void suspendedMemberIsNotEligible() {
        Member member = member(null);
        member.update("Ethan", "Brooks", LocalDate.of(1979, 12, 1), null, plan, START, null, MemberStatus.SUSPENDED);

        assertThat(member.eligibilityOn(LocalDate.of(2025, 5, 1)).reason()).isEqualTo(Eligibility.Reason.MEMBER_NOT_ACTIVE);
    }

    @Test
    void retiredPlanIsNotEligible() {
        Member member = member(null);
        plan.update("Gold", PlanType.PPO, new BigDecimal("1000.00"), 20, new BigDecimal("6000.00"), false);

        assertThat(member.eligibilityOn(LocalDate.of(2025, 5, 1)).reason()).isEqualTo(Eligibility.Reason.PLAN_INACTIVE);
    }

    @Test
    void coverageEndBeforeStartIsRejected() {
        assertThatThrownBy(() -> new Member("M1", "A", "B", LocalDate.of(1990, 1, 1), null, plan, START, START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planRejectsCoinsuranceOutsideRange() {
        assertThatThrownBy(() -> new Plan("X-1", "X", PlanType.HMO, BigDecimal.ZERO, 101, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Member member(LocalDate coverageEnd) {
        return new Member("M100001", "Maria", "Gonzalez", LocalDate.of(1988, 4, 12), "maria@example.com", plan, START, coverageEnd);
    }
}
