package com.claimflow.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.claimflow.common.security.CurrentUser;
import com.claimflow.member.domain.Eligibility;
import com.claimflow.member.domain.Member;
import com.claimflow.member.domain.Plan;
import com.claimflow.member.domain.PlanType;
import com.claimflow.member.repository.MemberRepository;
import com.claimflow.member.repository.PlanRepository;
import com.claimflow.member.web.dto.MemberDtos.MemberRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository members;
    @Mock
    PlanRepository plans;
    @InjectMocks
    MemberService service;

    private final Plan gold = new Plan("GOLD-PPO", "Gold PPO", PlanType.PPO, new BigDecimal("1000.00"), 20, new BigDecimal("6000.00"));

    @Test
    void memberCannotSeeAnotherMembersEligibility() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var user = new CurrentUser("maria", "MEMBER", Optional.of(own), "t");

        assertThatThrownBy(() -> service.eligibility(other, LocalDate.now(), user))
                .isInstanceOf(NotFoundException.class);
        verify(members, never()).findWithPlanById(any());
    }

    @Test
    void adjusterGetsEligibilityWithPlanTerms() {
        Member member = new Member("M100001", "Maria", "Gonzalez", LocalDate.of(1988, 4, 12), null, gold,
                LocalDate.of(2025, 1, 1), null);
        when(members.findWithPlanById(member.getId())).thenReturn(Optional.of(member));
        var adjuster = new CurrentUser("ava", "ADJUSTER", Optional.empty(), "t");

        var response = service.eligibility(member.getId(), LocalDate.of(2025, 3, 3), adjuster);

        assertThat(response.eligible()).isTrue();
        assertThat(response.reason()).isEqualTo(Eligibility.Reason.ELIGIBLE);
        assertThat(response.coinsurancePercent()).isEqualTo(20);
        assertThat(response.memberName()).isEqualTo("Maria Gonzalez");
    }

    @Test
    void enrollmentAssignsNextMemberNumber() {
        when(plans.findByCodeIgnoreCase("GOLD-PPO")).thenReturn(Optional.of(gold));
        when(members.nextMemberNumber()).thenReturn(100042L);
        when(members.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(request("new.person@example.com", "GOLD-PPO"));

        assertThat(created.memberNumber()).isEqualTo("M100042");
        assertThat(created.plan().code()).isEqualTo("GOLD-PPO");
    }

    @Test
    void enrollmentRejectsDuplicateEmail() {
        when(members.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("taken@example.com", "GOLD-PPO")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void enrollmentRejectsRetiredPlan() {
        gold.update("Gold PPO", PlanType.PPO, new BigDecimal("1000.00"), 20, new BigDecimal("6000.00"), false);
        when(plans.findByCodeIgnoreCase("GOLD-PPO")).thenReturn(Optional.of(gold));

        assertThatThrownBy(() -> service.create(request("a@example.com", "GOLD-PPO")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not open for enrollment");
    }

    private static MemberRequest request(String email, String planCode) {
        return new MemberRequest("New", "Person", LocalDate.of(1990, 1, 1), email, planCode,
                LocalDate.of(2026, 1, 1), null, null);
    }
}
