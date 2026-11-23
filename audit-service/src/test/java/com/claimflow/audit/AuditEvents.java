package com.claimflow.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.claimflow.common.event.ClaimStatusChangedEvent;

public final class AuditEvents {

    public static final UUID MEMBER_ID = UUID.fromString("eb47888d-3ffb-4ad2-940a-ae35e825b454");

    private AuditEvents() {
    }

    public static ClaimStatusChangedEvent event(UUID claimId, String from, String to, String actor, long version, Instant at) {
        return new ClaimStatusChangedEvent(UUID.randomUUID(), claimId, "CLM-2026-000007", MEMBER_ID, from, to, actor,
                "ADJUSTER", null, "APPROVED".equals(to) ? new BigDecimal("160.00") : null, version, at);
    }
}
