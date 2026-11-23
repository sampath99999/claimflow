package com.claimflow.audit.repository;

import static com.claimflow.audit.AuditEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.claimflow.audit.domain.AuditEntry;
import com.claimflow.audit.repository.AuditEntrySearch.ActivityFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataMongoTest
class AuditEntryRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");

    @Autowired
    AuditEntryRepository entries;

    @BeforeEach
    void clean() {
        entries.deleteAll();
    }

    @Test
    void timelineIsOrderedOldestFirst() {
        UUID claim = UUID.randomUUID();
        save(event(claim, "IN_REVIEW", "APPROVED", "ava", 2, T0.plusSeconds(120)));
        save(event(claim, null, "SUBMITTED", "maria", 0, T0));
        save(event(claim, "SUBMITTED", "IN_REVIEW", "ava", 1, T0.plusSeconds(60)));
        save(event(UUID.randomUUID(), null, "SUBMITTED", "james", 0, T0));

        var timeline = entries.findByClaimIdOrderByOccurredAtAscClaimVersionAsc(claim);

        assertThat(timeline).extracting(AuditEntry::toStatus).containsExactly("SUBMITTED", "IN_REVIEW", "APPROVED");
    }

    @Test
    void sameEventIdCannotBeInsertedTwice() {
        var event = event(UUID.randomUUID(), null, "SUBMITTED", "maria", 0, T0);
        save(event);

        assertThatThrownBy(() -> save(event)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void activitySearchFiltersByActorStatusAndTime() {
        UUID claim = UUID.randomUUID();
        save(event(claim, null, "SUBMITTED", "maria", 0, T0));
        save(event(claim, "SUBMITTED", "IN_REVIEW", "ava", 1, T0.plusSeconds(3600)));
        save(event(claim, "IN_REVIEW", "APPROVED", "ava", 2, T0.plusSeconds(7200)));

        var byAva = entries.search(new ActivityFilter("ava", null, null, null), PageRequest.of(0, 10, Sort.by("occurredAt")));
        var approvedInWindow = entries.search(new ActivityFilter(null, "APPROVED", T0, T0.plusSeconds(7201)), PageRequest.of(0, 10));
        var firstHour = entries.search(new ActivityFilter(null, null, T0, T0.plusSeconds(3600)), PageRequest.of(0, 1));

        assertThat(byAva.getContent()).extracting(AuditEntry::toStatus).containsExactly("IN_REVIEW", "APPROVED");
        assertThat(approvedInWindow.getTotalElements()).isEqualTo(1);
        assertThat(firstHour.getTotalElements()).isEqualTo(1);
        assertThat(firstHour.getContent()).singleElement().extracting(AuditEntry::actor).isEqualTo("maria");
    }

    private void save(com.claimflow.common.event.ClaimStatusChangedEvent event) {
        entries.insert(AuditEntry.from(event, Instant.now(), 0, 0));
    }
}
