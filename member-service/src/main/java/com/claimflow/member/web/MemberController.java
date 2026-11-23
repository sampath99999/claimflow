package com.claimflow.member.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.claimflow.common.security.CurrentUser;
import com.claimflow.common.web.PageResponse;
import com.claimflow.common.web.SortGuard;
import com.claimflow.member.domain.MemberStatus;
import com.claimflow.member.service.MemberService;
import com.claimflow.member.service.NotFoundException;
import com.claimflow.member.web.dto.MemberDtos.EligibilityResponse;
import com.claimflow.member.web.dto.MemberDtos.MemberRequest;
import com.claimflow.member.web.dto.MemberDtos.MemberResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Members")
public class MemberController {

    private static final Set<String> SORTABLE = Set.of("lastName", "firstName", "memberNumber", "coverageStart", "status");

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
    @Operation(summary = "Search members by name, member number or email")
    public PageResponse<MemberResponse> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) MemberStatus status,
                                               @RequestParam(required = false) String planCode,
                                               @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return memberService.search(q, status, planCode, SortGuard.allow(pageable, SORTABLE));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public MemberResponse me(Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        UUID memberId = user.memberId().orElseThrow(() -> new NotFoundException("No member linked to this login"));
        return memberService.get(memberId, user);
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable UUID id, Authentication authentication) {
        return memberService.get(id, CurrentUser.from(authentication));
    }

    @GetMapping("/{id}/eligibility")
    @Operation(summary = "Check whether the member is covered on a date of service")
    public EligibilityResponse eligibility(@PathVariable UUID id,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
                                           Authentication authentication) {
        return memberService.eligibility(id, serviceDate, CurrentUser.from(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enroll a member on a plan")
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(request);
        return ResponseEntity.created(URI.create("/api/members/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
        return memberService.update(id, request);
    }
}
