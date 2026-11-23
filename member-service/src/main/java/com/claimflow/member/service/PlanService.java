package com.claimflow.member.service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.claimflow.common.web.PageResponse;
import com.claimflow.member.domain.Plan;
import com.claimflow.member.repository.PlanRepository;
import com.claimflow.member.web.dto.PlanDtos.CreatePlanRequest;
import com.claimflow.member.web.dto.PlanDtos.PlanResponse;
import com.claimflow.member.web.dto.PlanDtos.UpdatePlanRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final PlanRepository plans;

    public PlanService(PlanRepository plans) {
        this.plans = plans;
    }

    public PageResponse<PlanResponse> search(String q, boolean activeOnly, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        Page<Plan> page = plans.search(term, activeOnly, pageable);
        Map<UUID, Long> counts = plans.countMembers(page.map(Plan::getId).getContent()).stream()
                .collect(Collectors.toMap(PlanRepository.PlanMemberCount::getPlanId, PlanRepository.PlanMemberCount::getMembers));
        return PageResponse.of(page.map(plan -> PlanResponse.from(plan, counts.getOrDefault(plan.getId(), 0L))));
    }

    public PlanResponse get(UUID id) {
        Plan plan = find(id);
        return PlanResponse.from(plan, memberCount(plan));
    }

    @Transactional
    public PlanResponse create(CreatePlanRequest request) {
        if (plans.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Plan code " + request.code() + " already exists");
        }
        Plan plan = plans.save(new Plan(request.code(), request.name(), request.type(), request.annualDeductible(),
                request.coinsurancePercent(), request.outOfPocketMax()));
        log.info("Created plan {} ({})", plan.getCode(), plan.getId());
        return PlanResponse.from(plan, 0);
    }

    @Transactional
    public PlanResponse update(UUID id, UpdatePlanRequest request) {
        Plan plan = find(id);
        plan.update(request.name(), request.type(), request.annualDeductible(), request.coinsurancePercent(),
                request.outOfPocketMax(), request.active());
        plans.flush();
        log.info("Updated plan {} active={}", plan.getCode(), plan.isActive());
        return PlanResponse.from(plan, memberCount(plan));
    }

    private Plan find(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Plan " + id + " not found"));
    }

    private long memberCount(Plan plan) {
        return plans.countMembers(java.util.List.of(plan.getId())).stream()
                .mapToLong(PlanRepository.PlanMemberCount::getMembers)
                .sum();
    }
}
