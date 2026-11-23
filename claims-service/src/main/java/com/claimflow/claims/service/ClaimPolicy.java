package com.claimflow.claims.service;

import java.util.EnumSet;
import java.util.Set;

import com.claimflow.claims.config.ClaimsProperties;
import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimAction;
import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.security.Roles;

import org.springframework.stereotype.Component;

/**
 * Who may do what to a claim. Kept apart from the state machine: the state machine says
 * whether a move is legal at all, this says whether this user may make it.
 */
@Component
public class ClaimPolicy {

    private final ClaimsProperties properties;

    public ClaimPolicy(ClaimsProperties properties) {
        this.properties = properties;
    }

    public Set<ClaimAction> allowedActions(Claim claim, CurrentUser user) {
        Set<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        for (ClaimAction action : ClaimAction.values()) {
            if (claim.getStatus().canMoveTo(action.target()) && denialReason(action, claim, user) == null) {
                actions.add(action);
            }
        }
        return actions;
    }

    /**
     * Returns null when allowed, otherwise a message suitable for a 403 response.
     */
    public String denialReason(ClaimAction action, Claim claim, CurrentUser user) {
        if (!user.isStaff()) {
            return "Only adjusters and admins can act on claims";
        }
        if (action == ClaimAction.MARK_PAID && !Roles.ADMIN.equals(user.role())) {
            return "Only admins can mark claims as paid";
        }
        if (action == ClaimAction.APPROVE || action == ClaimAction.DENY) {
            if (user.username().equalsIgnoreCase(claim.getSubmittedBy())) {
                return "You cannot decide a claim you submitted";
            }
        }
        if (action == ClaimAction.APPROVE && Roles.ADJUSTER.equals(user.role())
                && claim.getTotalBilled().compareTo(properties.adjusterApprovalLimit()) > 0) {
            return "Claims above " + properties.adjusterApprovalLimit() + " need admin approval";
        }
        return null;
    }
}
