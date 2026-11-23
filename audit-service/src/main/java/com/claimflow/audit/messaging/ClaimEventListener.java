package com.claimflow.audit.messaging;

import java.time.Clock;

import com.claimflow.audit.domain.AuditEntry;
import com.claimflow.audit.repository.AuditEntryRepository;
import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class ClaimEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventListener.class);

    private final AuditEntryRepository entries;
    private final Clock clock;

    public ClaimEventListener(AuditEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @KafkaListener(topics = ClaimStatusChangedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void onStatusChanged(@Payload ClaimStatusChangedEvent event,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset) {
        if (event.eventId() == null || event.claimId() == null || event.toStatus() == null) {
            throw new IllegalArgumentException("Event is missing eventId, claimId or toStatus: " + event);
        }
        try {
            entries.insert(AuditEntry.from(event, clock.instant(), partition, offset));
            log.info("Recorded {} {} -> {} by {} (p{} o{})", event.claimNumber(), event.fromStatus(), event.toStatus(),
                    event.actor(), partition, offset);
        } catch (DuplicateKeyException e) {
            log.info("Skipping duplicate event {} for {}", event.eventId(), event.claimNumber());
        }
    }
}
