package com.claimflow.claims.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.claimflow.claims.client.EligibilityResult;
import com.claimflow.claims.client.MemberClient;
import com.claimflow.claims.client.MemberNotFoundException;
import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimStatus;
import com.claimflow.claims.repository.ClaimRepository;
import com.claimflow.claims.repository.ClaimSpecifications;
import com.claimflow.claims.repository.ClaimSpecifications.ClaimFilter;
import com.claimflow.claims.repository.StatusCount;
import com.claimflow.claims.validation.ClaimRejectedException;
import com.claimflow.claims.validation.ClaimRules;
import com.claimflow.claims.validation.Violation;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimActionRequest;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimDetail;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimSummary;
import com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest;
import com.claimflow.common.event.ClaimStatusChangedEvent;
import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.web.PageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claims;
    private final ClaimRules rules;
    private final ClaimPolicy policy;
    private final MemberClient memberClient;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimRules rules, ClaimPolicy policy, MemberClient memberClient,
                        ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.claims = claims;
        this.rules = rules;
        this.policy = policy;
        this.memberClient = memberClient;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Not transactional on purpose: the eligibility call goes over the network and should not
     * hold a database connection. Only the final insert runs in a transaction.
     */
    public ClaimDetail submit(SubmitClaimRequest request, CurrentUser user) {
        UUID memberId = resolveMember(request, user);

        List<Violation> violations = rules.check(request);
        if (!violations.isEmpty()) {
            throw new ClaimRejectedException(violations);
        }

        EligibilityResult eligibility = checkEligibility(memberId, request, user);
        if (!eligibility.eligible()) {
            throw new ClaimRejectedException(new Violation("MEMBER_NOT_ELIGIBLE",
                    "Member is not covered on " + request.serviceDate() + " (" + eligibility.reason() + ")"));
        }

        BigDecimal total = request.lines().stream()
                .map(line -> line.chargeAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Claim saved = tx.execute(status -> {
            if (claims.existsDuplicate(memberId, request.providerNpi(), request.serviceDate(), total, ClaimStatus.DENIED)) {
                throw new ClaimRejectedException(new Violation("DUPLICATE_CLAIM",
                        "A claim for this member, provider, date and amount already exists"));
            }
            Claim claim = Claim.submit(
                    nextClaimNumber(),
                    new Claim.MemberSnapshot(memberId, eligibility.memberNumber(), eligibility.memberName(),
                            eligibility.planCode(), eligibility.coinsurancePercent()),
                    request.providerNpi(),
                    request.providerName().trim(),
                    request.serviceDate(),
                    request.diagnosisCode(),
                    user.username(),
                    request.lines().stream()
                            .map(l -> new Claim.LineItem(l.procedureCode(), l.description().trim(), l.units(), l.chargeAmount()))
                            .toList());
            claims.saveAndFlush(claim);
            events.publishEvent(event(claim, null, ClaimStatus.SUBMITTED, user, null, claim.getTotalBilled()));
            return claim;
        });

        log.info("Claim {} submitted by {} for member {} total={}", saved.getClaimNumber(), user.username(),
                saved.getMemberNumber(), saved.getTotalBilled());
        return ClaimDetail.from(saved, policy.allowedActions(saved, user));
    }

    @Transactional
    public ClaimDetail apply(UUID id, long expectedVersion, ClaimActionRequest request, CurrentUser user) {
        Claim claim = findVisible(id, user);
        if (claim.getVersion() != expectedVersion) {
            throw new StaleClaimException(id, expectedVersion, claim.getVersion());
        }
        String denial = policy.denialReason(request.action(), claim, user);
        if (denial != null) {
            throw new ActionNotAllowedException(denial);
        }

        Claim.StatusChange change = claim.apply(request.action(), user.username(), blankToNull(request.note()));
        // Flush now so a concurrent update fails here, before the event is queued.
        claims.flush();
        events.publishEvent(event(claim, change.from(), change.to(), user, change.note(), change.amount()));

        log.info("Claim {} {} -> {} by {}", claim.getClaimNumber(), change.from(), change.to(), user.username());
        return ClaimDetail.from(claim, policy.allowedActions(claim, user));
    }

    @Transactional(readOnly = true)
    public ClaimDetail get(UUID id, CurrentUser user) {
        Claim claim = findVisible(id, user);
        return ClaimDetail.from(claim, policy.allowedActions(claim, user));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummary> search(ClaimFilter filter, Pageable pageable, CurrentUser user) {
        ClaimFilter scoped = user.isMember()
                ? new ClaimFilter(filter.statuses(), user.memberId().orElseThrow(), filter.serviceFrom(),
                        filter.serviceTo(), filter.q(), filter.assignedTo())
                : filter;
        return PageResponse.of(claims.findAll(ClaimSpecifications.matching(scoped), pageable).map(ClaimSummary::from));
    }

    @Transactional(readOnly = true)
    public List<StatusCount> statusCounts() {
        return claims.countByStatus();
    }

    private Claim findVisible(UUID id, CurrentUser user) {
        Claim claim = claims.findWithLinesById(id).orElseThrow(() -> new ClaimNotFoundException(id));
        if (!user.canAccessMember(claim.getMemberId())) {
            throw new ClaimNotFoundException(id);
        }
        return claim;
    }

    private UUID resolveMember(SubmitClaimRequest request, CurrentUser user) {
        if (user.isMember()) {
            UUID own = user.memberId().orElseThrow(() -> new ActionNotAllowedException("Login is not linked to a member"));
            if (request.memberId() != null && !request.memberId().equals(own)) {
                throw new ActionNotAllowedException("Members can only submit claims for themselves");
            }
            return own;
        }
        if (request.memberId() == null) {
            throw new ClaimRejectedException(new Violation("MEMBER_REQUIRED", "memberId is required when staff submit a claim"));
        }
        return request.memberId();
    }

    private EligibilityResult checkEligibility(UUID memberId, SubmitClaimRequest request, CurrentUser user) {
        try {
            return memberClient.checkEligibility(memberId, request.serviceDate(), user.bearerToken());
        } catch (MemberNotFoundException e) {
            throw new ClaimRejectedException(new Violation("MEMBER_NOT_FOUND", e.getMessage()));
        }
    }

    private String nextClaimNumber() {
        int year = Year.now(clock.withZone(ZoneOffset.UTC)).getValue();
        return "CLM-%d-%06d".formatted(year, claims.nextClaimSequence());
    }

    private ClaimStatusChangedEvent event(Claim claim, ClaimStatus from, ClaimStatus to, CurrentUser user,
                                          String note, BigDecimal amount) {
        return new ClaimStatusChangedEvent(
                UUID.randomUUID(),
                claim.getId(),
                claim.getClaimNumber(),
                claim.getMemberId(),
                from == null ? null : from.name(),
                to.name(),
                user.username(),
                user.role(),
                note,
                amount,
                claim.getVersion(),
                Instant.now(clock));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
