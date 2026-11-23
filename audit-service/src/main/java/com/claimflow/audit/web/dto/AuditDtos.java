package com.claimflow.audit.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.claimflow.audit.domain.AuditEntry;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record ClaimTimeline(UUID claimId, String claimNumber, int events, Instant lastChangeAt,
                                List<TimelineEntry> entries) {
    }

    public record TimelineEntry(
            UUID eventId,
            UUID claimId,
            String claimNumber,
            String fromStatus,
            String toStatus,
            String actor,
            String actorRole,
            String note,
            BigDecimal amount,
            long claimVersion,
            Instant occurredAt) {

        public static TimelineEntry from(AuditEntry e) {
            return new TimelineEntry(e.eventId(), e.claimId(), e.claimNumber(), e.fromStatus(), e.toStatus(),
                    e.actor(), e.actorRole(), e.note(), e.amount(), e.claimVersion(), e.occurredAt());
        }
    }
}
