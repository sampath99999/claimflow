package com.claimflow.claims.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.claimflow.claims.domain.ClaimStatus;
import com.claimflow.claims.repository.ClaimSpecifications.ClaimFilter;
import com.claimflow.claims.repository.StatusCount;
import com.claimflow.claims.service.ClaimService;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimActionRequest;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimDetail;
import com.claimflow.claims.web.dto.ClaimDtos.ClaimSummary;
import com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest;
import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.web.PageResponse;
import com.claimflow.common.web.SortGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "Claims")
public class ClaimController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "serviceDate", "totalBilled", "claimNumber", "status");

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @Operation(summary = "Submit a claim. Members submit for themselves; staff pass memberId.")
    public ResponseEntity<ClaimDetail> submit(@Valid @RequestBody SubmitClaimRequest request, Authentication authentication) {
        ClaimDetail claim = claimService.submit(request, CurrentUser.from(authentication));
        return ResponseEntity.created(URI.create("/api/claims/" + claim.id()))
                .eTag(ETags.of(claim.version()))
                .body(claim);
    }

    @GetMapping
    @Operation(summary = "List claims with filters. Members only ever see their own.")
    public PageResponse<ClaimSummary> search(
            @RequestParam(required = false) Set<ClaimStatus> status,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceTo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String assignedTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        var filter = new ClaimFilter(status, memberId, serviceFrom, serviceTo, q, assignedTo);
        return claimService.search(filter, SortGuard.allow(pageable, SORTABLE), CurrentUser.from(authentication));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
    @Operation(summary = "Claim counts and billed totals by status")
    public List<StatusCount> stats() {
        return claimService.statusCounts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimDetail> get(@PathVariable UUID id, Authentication authentication) {
        ClaimDetail claim = claimService.get(id, CurrentUser.from(authentication));
        return ResponseEntity.ok().eTag(ETags.of(claim.version())).body(claim);
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
    @Operation(summary = "Move a claim through review. Send the ETag from GET as If-Match.")
    public ResponseEntity<ClaimDetail> act(@PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                           @Valid @RequestBody ClaimActionRequest request,
                                           Authentication authentication) {
        long expectedVersion = ETags.requireVersion(ifMatch);
        ClaimDetail claim = claimService.apply(id, expectedVersion, request, CurrentUser.from(authentication));
        return ResponseEntity.ok().eTag(ETags.of(claim.version())).body(claim);
    }
}
