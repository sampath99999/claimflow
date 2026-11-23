package com.claimflow.claims.event;

import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends status events to Kafka only after the database commit, so consumers never see a
 * change that was rolled back. The gap (commit succeeded, send failed) is logged; closing it
 * properly needs a transactional outbox, which is listed in the README as next work.
 */
@Component
public class ClaimEventRelay {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventRelay.class);

    private final KafkaTemplate<String, ClaimStatusChangedEvent> kafka;

    public ClaimEventRelay(KafkaTemplate<String, ClaimStatusChangedEvent> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ClaimStatusChangedEvent event) {
        kafka.send(ClaimStatusChangedEvent.TOPIC, event.claimId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish {} for claim {} ({} -> {})", event.eventId(), event.claimNumber(),
                                event.fromStatus(), event.toStatus(), error);
                    } else {
                        log.debug("Published {} to partition {} offset {}", event.eventId(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
