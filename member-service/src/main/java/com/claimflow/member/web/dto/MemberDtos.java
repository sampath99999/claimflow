package com.claimflow.member.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.claimflow.member.domain.Eligibility;
import com.claimflow.member.domain.Member;
import com.claimflow.member.domain.MemberStatus;
import com.claimflow.member.domain.Plan;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public final class MemberDtos {

    private MemberDtos() {
    }

    public record MemberRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            @Email @Size(max = 160) String email,
            @NotBlank String planCode,
            @NotNull LocalDate coverageStart,
            LocalDate coverageEnd,
            MemberStatus status) {
    }

    public record MemberResponse(
            UUID id,
            String memberNumber,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String email,
            PlanSummary plan,
            LocalDate coverageStart,
            LocalDate coverageEnd,
            MemberStatus status,
            Instant updatedAt) {

        public static MemberResponse from(Member member) {
            return new MemberResponse(member.getId(), member.getMemberNumber(), member.getFirstName(),
                    member.getLastName(), member.getDateOfBirth(), member.getEmail(),
                    PlanSummary.from(member.getPlan()), member.getCoverageStart(), member.getCoverageEnd(),
                    member.getStatus(), member.getUpdatedAt());
        }
    }

    public record PlanSummary(UUID id, String code, String name) {
        static PlanSummary from(Plan plan) {
            return new PlanSummary(plan.getId(), plan.getCode(), plan.getName());
        }
    }

    public record EligibilityResponse(
            UUID memberId,
            String memberNumber,
            String memberName,
            LocalDate serviceDate,
            boolean eligible,
            Eligibility.Reason reason,
            String planCode,
            int coinsurancePercent,
            BigDecimal annualDeductible) {

        public static EligibilityResponse of(Member member, LocalDate serviceDate, Eligibility eligibility) {
            Plan plan = member.getPlan();
            return new EligibilityResponse(member.getId(), member.getMemberNumber(),
                    member.getFirstName() + " " + member.getLastName(), serviceDate,
                    eligibility.eligible(), eligibility.reason(), plan.getCode(),
                    plan.getCoinsurancePercent(), plan.getAnnualDeductible());
        }
    }
}
