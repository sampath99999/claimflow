package com.claimflow.claims.domain;

import static com.claimflow.claims.ClaimFixtures.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ClaimTest {

    @Test
    void newClaimIsSubmittedWithNumberedLinesAndTotal() {
        Claim claim = claim("maria", "150.00", "45.50");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getTotalBilled()).isEqualByComparingTo("195.50");
        assertThat(claim.getLines()).extracting(ClaimLine::getLineNumber).containsExactly(1, 2);
    }

    @Test
    void happyPathThroughReviewApprovalAndPayment() {
        Claim claim = claim("maria", "200.00");

        claim.apply(ClaimAction.START_REVIEW, "ava", null);
        var approval = claim.apply(ClaimAction.APPROVE, "ava", "Matches fee schedule");
        var payment = claim.apply(ClaimAction.MARK_PAID, "admin", null);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(claim.getAssignedTo()).isEqualTo("ava");
        assertThat(approval.from()).isEqualTo(ClaimStatus.IN_REVIEW);
        assertThat(payment.amount()).isEqualByComparingTo("160.00");
    }

    @ParameterizedTest
    @CsvSource({"0, 1000.00", "20, 800.00", "35, 650.00", "100, 0.00"})
    void approvalSplitsTotalByCoinsurance(int coinsurance, String payable) {
        Claim claim = Claim.submit("CLM-1", new Claim.MemberSnapshot(java.util.UUID.randomUUID(), "M1", "X", "P", coinsurance),
                "1234567893", "Clinic", java.time.LocalDate.now(), "J02.9", "maria",
                java.util.List.of(new Claim.LineItem("99213", "Visit", 1, new BigDecimal("1000.00"))));
        claim.apply(ClaimAction.START_REVIEW, "ava", null);

        claim.apply(ClaimAction.APPROVE, "ava", null);

        assertThat(claim.getPayableAmount()).isEqualByComparingTo(payable);
        assertThat(claim.getPayableAmount().add(claim.getMemberResponsibility())).isEqualByComparingTo("1000.00");
    }

    @Test
    void cannotApproveWithoutReview() {
        Claim claim = claim("maria", "100.00");

        assertThatThrownBy(() -> claim.apply(ClaimAction.APPROVE, "ava", null))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("SUBMITTED to APPROVED");
    }

    @Test
    void denialNeedsReasonAndLeavesFullAmountWithMember() {
        Claim claim = claim("maria", "100.00");
        claim.apply(ClaimAction.START_REVIEW, "ava", null);

        assertThatThrownBy(() -> claim.apply(ClaimAction.DENY, "ava", " "))
                .isInstanceOf(IllegalArgumentException.class);

        claim.apply(ClaimAction.DENY, "ava", "Service not covered under plan");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.getPayableAmount()).isEqualByComparingTo("0");
        assertThat(claim.getMemberResponsibility()).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @EnumSource(value = ClaimAction.class)
    void finalStatesAcceptNoActions(ClaimAction action) {
        Claim claim = claim("maria", "100.00");
        claim.apply(ClaimAction.START_REVIEW, "ava", null);
        claim.apply(ClaimAction.DENY, "ava", "Duplicate of earlier claim");

        assertThatThrownBy(() -> claim.apply(action, "ava", "x")).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void statusGraphMatchesWorkflow() {
        assertThat(ClaimStatus.SUBMITTED.allowedNext()).containsExactly(ClaimStatus.IN_REVIEW);
        assertThat(ClaimStatus.IN_REVIEW.allowedNext()).containsExactlyInAnyOrder(ClaimStatus.APPROVED, ClaimStatus.DENIED);
        assertThat(ClaimStatus.APPROVED.allowedNext()).containsExactly(ClaimStatus.PAID);
        assertThat(ClaimStatus.PAID.isFinal()).isTrue();
        assertThat(ClaimStatus.DENIED.isFinal()).isTrue();
    }
}
