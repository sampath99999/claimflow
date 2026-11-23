package com.claimflow.audit.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.claimflow.audit.repository.AuditEntrySearch.ActivityFilter;
import com.claimflow.audit.service.TimelineService;
import com.claimflow.audit.web.dto.AuditDtos.ClaimTimeline;
import com.claimflow.audit.web.dto.AuditDtos.TimelineEntry;
import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.web.PageResponse;
import com.claimflow.common.web.SortGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit")
public class AuditController {

    private static final Set<String> SORTABLE = Set.of("occurredAt", "actor", "toStatus");

    private final TimelineService timelineService;

    public AuditController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping("/claims/{claimId}/timeline")
    @Operation(summary = "Every status change of a claim, oldest first")
    public ClaimTimeline timeline(@PathVariable UUID claimId, Authentication authentication) {
        return timelineService.timeline(claimId, CurrentUser.from(authentication));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
    @Operation(summary = "Recent activity across all claims")
    public PageResponse<TimelineEntry> activity(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String toStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return timelineService.activity(new ActivityFilter(actor, toStatus, from, to), SortGuard.allow(pageable, SORTABLE));
    }
}
