package com.claimflow.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by claims-service on every status change, including the initial submission
 * (fromStatus is null then). Keyed by claimId so all events for a claim land on one partition.
 */
public record ClaimStatusChangedEvent(
        UUID eventId,
        UUID claimId,
        String claimNumber,
        UUID memberId,
        String fromStatus,
        String toStatus,
        String actor,
        String actorRole,
        String note,
        BigDecimal amount,
        long claimVersion,
        Instant occurredAt) {

    public static final String TOPIC = "claims.status-changed.v1";
}
