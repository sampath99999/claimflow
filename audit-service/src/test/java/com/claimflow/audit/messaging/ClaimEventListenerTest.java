package com.claimflow.audit.messaging;

import static com.claimflow.audit.AuditEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.claimflow.audit.domain.AuditEntry;
import com.claimflow.audit.repository.AuditEntryRepository;
import com.claimflow.common.event.ClaimStatusChangedEvent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ClaimEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T09:30:00Z");

    @Mock
    AuditEntryRepository entries;

    @Test
    void storesEventWithKafkaPosition() {
        var listener = new ClaimEventListener(entries, Clock.fixed(NOW, ZoneOffset.UTC));
        ClaimStatusChangedEvent event = event(UUID.randomUUID(), "IN_REVIEW", "APPROVED", "ava", 2, NOW.minusSeconds(3));

        listener.onStatusChanged(event, 1, 42L);

        var saved = ArgumentCaptor.forClass(AuditEntry.class);
        verify(entries).insert(saved.capture());
        assertThat(saved.getValue().eventId()).isEqualTo(event.eventId());
        assertThat(saved.getValue().partition()).isEqualTo(1);
        assertThat(saved.getValue().offset()).isEqualTo(42L);
        assertThat(saved.getValue().recordedAt()).isEqualTo(NOW);
    }

    @Test
    void redeliveredEventIsIgnored() {
        var listener = new ClaimEventListener(entries, Clock.systemUTC());
        when(entries.insert(any(AuditEntry.class))).thenThrow(new DuplicateKeyException("E11000"));

        listener.onStatusChanged(event(UUID.randomUUID(), null, "SUBMITTED", "maria", 0, NOW), 0, 1L);
    }

    @Test
    void eventWithoutIdentifiersIsRejected() {
        var listener = new ClaimEventListener(entries, Clock.systemUTC());
        var broken = new ClaimStatusChangedEvent(null, null, null, null, null, null, null, null, null, null, 0, null);

        assertThatThrownBy(() -> listener.onStatusChanged(broken, 0, 0L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(entries);
    }
}
