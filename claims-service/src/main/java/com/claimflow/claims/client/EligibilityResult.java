package com.claimflow.claims.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityResult(
        UUID memberId,
        String memberNumber,
        String memberName,
        LocalDate serviceDate,
        boolean eligible,
        String reason,
        String planCode,
        int coinsurancePercent,
        BigDecimal annualDeductible) {
}
