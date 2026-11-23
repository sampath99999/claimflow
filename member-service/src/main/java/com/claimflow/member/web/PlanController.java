package com.claimflow.member.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import com.claimflow.common.web.PageResponse;
import com.claimflow.common.web.SortGuard;
import com.claimflow.member.service.PlanService;
import com.claimflow.member.web.dto.PlanDtos.CreatePlanRequest;
import com.claimflow.member.web.dto.PlanDtos.PlanResponse;
import com.claimflow.member.web.dto.PlanDtos.UpdatePlanRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
@Tag(name = "Plans")
public class PlanController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "type", "coinsurancePercent", "annualDeductible");

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "Search plans by code or name")
    public PageResponse<PlanResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "false") boolean activeOnly,
                                             @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return planService.search(q, activeOnly, SortGuard.allow(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable UUID id) {
        return planService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a plan")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse created = planService.create(request);
        return ResponseEntity.created(URI.create("/api/plans/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update plan terms or retire a plan")
    public PlanResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        return planService.update(id, request);
    }
}
