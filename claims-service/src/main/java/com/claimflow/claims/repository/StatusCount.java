package com.claimflow.claims.repository;

import java.math.BigDecimal;

import com.claimflow.claims.domain.ClaimStatus;

public record StatusCount(ClaimStatus status, long count, BigDecimal totalBilled) {
}
