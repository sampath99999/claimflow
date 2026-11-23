package com.claimflow.member.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.claimflow.member.domain.Plan;
import com.claimflow.member.domain.PlanType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PlanDtos {

    private PlanDtos() {
    }

    public record CreatePlanRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{3,32}", message = "must be 3-32 upper case letters, digits or dashes") String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull PlanType type,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal annualDeductible,
            @Min(0) @Max(100) int coinsurancePercent,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal outOfPocketMax) {
    }

    public record UpdatePlanRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull PlanType type,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal annualDeductible,
            @Min(0) @Max(100) int coinsurancePercent,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal outOfPocketMax,
            boolean active) {
    }

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            PlanType type,
            BigDecimal annualDeductible,
            int coinsurancePercent,
            BigDecimal outOfPocketMax,
            boolean active,
            long memberCount,
            Instant updatedAt) {

        public static PlanResponse from(Plan plan, long memberCount) {
            return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getType(),
                    plan.getAnnualDeductible(), plan.getCoinsurancePercent(), plan.getOutOfPocketMax(),
                    plan.isActive(), memberCount, plan.getUpdatedAt());
        }
    }
}
