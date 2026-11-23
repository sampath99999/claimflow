package com.claimflow.member.service;

import java.time.LocalDate;
import java.util.UUID;

import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.web.PageResponse;
import com.claimflow.member.domain.Member;
import com.claimflow.member.domain.MemberStatus;
import com.claimflow.member.domain.Plan;
import com.claimflow.member.repository.MemberRepository;
import com.claimflow.member.repository.MemberSpecifications;
import com.claimflow.member.repository.PlanRepository;
import com.claimflow.member.web.dto.MemberDtos.EligibilityResponse;
import com.claimflow.member.web.dto.MemberDtos.MemberRequest;
import com.claimflow.member.web.dto.MemberDtos.MemberResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository members;
    private final PlanRepository plans;

    public MemberService(MemberRepository members, PlanRepository plans) {
        this.members = members;
        this.plans = plans;
    }

    public PageResponse<MemberResponse> search(String q, MemberStatus status, String planCode, Pageable pageable) {
        return PageResponse.of(members.findAll(MemberSpecifications.search(q, status, planCode), pageable)
                .map(MemberResponse::from));
    }

    public MemberResponse get(UUID id, CurrentUser user) {
        return MemberResponse.from(findVisible(id, user));
    }

    public EligibilityResponse eligibility(UUID id, LocalDate serviceDate, CurrentUser user) {
        Member member = findVisible(id, user);
        var eligibility = member.eligibilityOn(serviceDate);
        log.debug("Eligibility member={} date={} -> {}", member.getMemberNumber(), serviceDate, eligibility.reason());
        return EligibilityResponse.of(member, serviceDate, eligibility);
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        if (request.email() != null && members.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A member with email " + request.email() + " already exists");
        }
        Plan plan = activePlan(request.planCode());
        String memberNumber = "M" + members.nextMemberNumber();
        Member member = new Member(memberNumber, request.firstName(), request.lastName(), request.dateOfBirth(),
                request.email(), plan, request.coverageStart(), request.coverageEnd());
        members.save(member);
        log.info("Enrolled member {} on plan {}", memberNumber, plan.getCode());
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(UUID id, MemberRequest request) {
        Member member = members.findWithPlanById(id).orElseThrow(() -> notFound(id));
        Plan plan = member.getPlan().getCode().equalsIgnoreCase(request.planCode())
                ? member.getPlan()
                : activePlan(request.planCode());
        MemberStatus status = request.status() == null ? member.getStatus() : request.status();
        member.update(request.firstName(), request.lastName(), request.dateOfBirth(), request.email(), plan,
                request.coverageStart(), request.coverageEnd(), status);
        members.flush();
        log.info("Updated member {} status={} plan={}", member.getMemberNumber(), status, plan.getCode());
        return MemberResponse.from(member);
    }

    private Member findVisible(UUID id, CurrentUser user) {
        // Members asking for someone else get the same 404 as a missing id, so ids can't be probed.
        if (!user.canAccessMember(id)) {
            throw notFound(id);
        }
        return members.findWithPlanById(id).orElseThrow(() -> notFound(id));
    }

    private Plan activePlan(String code) {
        Plan plan = plans.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("Plan " + code + " not found"));
        if (!plan.isActive()) {
            throw new ConflictException("Plan " + code + " is not open for enrollment");
        }
        return plan;
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("Member " + id + " not found");
    }
}
