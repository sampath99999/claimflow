package com.claimflow.claims.service;

import static com.claimflow.claims.ClaimFixtures.claim;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

import com.claimflow.claims.config.ClaimsProperties;
import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimAction;
import com.claimflow.common.security.CurrentUser;

import org.junit.jupiter.api.Test;

class ClaimPolicyTest {

    private static final CurrentUser MEMBER = new CurrentUser("maria", "MEMBER", Optional.of(com.claimflow.claims.ClaimFixtures.MEMBER_ID), "t");
    private static final CurrentUser ADJUSTER = new CurrentUser("ava", "ADJUSTER", Optional.empty(), "t");
    private static final CurrentUser ADMIN = new CurrentUser("admin", "ADMIN", Optional.empty(), "t");

    private final ClaimPolicy policy = new ClaimPolicy(new ClaimsProperties(365, new BigDecimal("100000.00"),
            new BigDecimal("25000.00"), new ClaimsProperties.MemberService(URI.create("http://x"), null, null)));

    @Test
    void membersGetNoActions() {
        assertThat(policy.allowedActions(claim("maria", "100.00"), MEMBER)).isEmpty();
    }

    @Test
    void adjusterCanStartReviewThenDecide() {
        Claim claim = claim("maria", "100.00");
        assertThat(policy.allowedActions(claim, ADJUSTER)).containsExactly(ClaimAction.START_REVIEW);

        claim.apply(ClaimAction.START_REVIEW, "ava", null);
        assertThat(policy.allowedActions(claim, ADJUSTER)).containsExactlyInAnyOrder(ClaimAction.APPROVE, ClaimAction.DENY);
    }

    @Test
    void onlyAdminMarksPaid() {
        Claim claim = claim("maria", "100.00");
        claim.apply(ClaimAction.START_REVIEW, "ava", null);
        claim.apply(ClaimAction.APPROVE, "ava", null);

        assertThat(policy.allowedActions(claim, ADJUSTER)).isEmpty();
        assertThat(policy.allowedActions(claim, ADMIN)).containsExactly(ClaimAction.MARK_PAID);
    }

    @Test
    void staffCannotDecideClaimsTheySubmitted() {
        Claim claim = claim("ava", "100.00");
        claim.apply(ClaimAction.START_REVIEW, "ava", null);

        assertThat(policy.denialReason(ClaimAction.APPROVE, claim, ADJUSTER)).contains("you submitted");
        assertThat(policy.denialReason(ClaimAction.APPROVE, claim, ADMIN)).isNull();
    }

    @Test
    void highValueApprovalNeedsAdmin() {
        Claim claim = claim("maria", "20000.00", "5000.01");
        claim.apply(ClaimAction.START_REVIEW, "ava", null);

        assertThat(policy.allowedActions(claim, ADJUSTER)).containsExactly(ClaimAction.DENY);
        assertThat(policy.allowedActions(claim, ADMIN)).containsExactlyInAnyOrder(ClaimAction.APPROVE, ClaimAction.DENY);
    }
}
