package com.claimflow.audit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One row per status change. The Kafka event id is the document id, which makes the consumer
 * idempotent: a redelivered event fails the insert instead of creating a second entry.
 */
@Document("claim_audit")
@CompoundIndex(name = "claim_timeline", def = "{'claimId': 1, 'occurredAt': 1}")
public record AuditEntry(
        @Id UUID eventId,
        UUID claimId,
        String claimNumber,
        @Indexed UUID memberId,
        String fromStatus,
        String toStatus,
        @Indexed String actor,
        String actorRole,
        String note,
        BigDecimal amount,
        long claimVersion,
        @Indexed Instant occurredAt,
        Instant recordedAt,
        int partition,
        long offset) {

    public static AuditEntry from(ClaimStatusChangedEvent event, Instant recordedAt, int partition, long offset) {
        return new AuditEntry(event.eventId(), event.claimId(), event.claimNumber(), event.memberId(),
                event.fromStatus(), event.toStatus(), event.actor(), event.actorRole(), event.note(),
                event.amount(), event.claimVersion(), event.occurredAt(), recordedAt, partition, offset);
    }
}
