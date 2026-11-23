package com.claimflow.audit.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.claimflow.audit.domain.AuditEntry;
import com.claimflow.audit.repository.AuditEntryRepository;
import com.claimflow.audit.repository.AuditEntrySearch.ActivityFilter;
import com.claimflow.audit.web.dto.AuditDtos.ClaimTimeline;
import com.claimflow.audit.web.dto.AuditDtos.TimelineEntry;
import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.web.PageResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TimelineService {

    private final AuditEntryRepository entries;

    public TimelineService(AuditEntryRepository entries) {
        this.entries = entries;
    }

    public ClaimTimeline timeline(UUID claimId, CurrentUser user) {
        List<AuditEntry> found = entries.findByClaimIdOrderByOccurredAtAscClaimVersionAsc(claimId);
        if (!found.isEmpty() && !user.canAccessMember(found.getFirst().memberId())) {
            throw new TimelineNotFoundException(claimId);
        }
        String claimNumber = found.isEmpty() ? null : found.getFirst().claimNumber();
        Instant lastChange = found.isEmpty() ? null : found.getLast().occurredAt();
        return new ClaimTimeline(claimId, claimNumber, found.size(), lastChange,
                found.stream().map(TimelineEntry::from).toList());
    }

    public PageResponse<TimelineEntry> activity(ActivityFilter filter, Pageable pageable) {
        return PageResponse.of(entries.search(filter, pageable).map(TimelineEntry::from));
    }

    public static class TimelineNotFoundException extends RuntimeException {
        public TimelineNotFoundException(UUID claimId) {
            super("No audit trail for claim " + claimId);
        }
    }
}
