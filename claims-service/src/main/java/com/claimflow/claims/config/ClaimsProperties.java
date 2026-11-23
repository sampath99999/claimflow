package com.claimflow.claims.config;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "claimflow.claims")
public record ClaimsProperties(
        @DefaultValue("365") @Positive int timelyFilingDays,
        @DefaultValue("100000.00") @NotNull BigDecimal maxClaimAmount,
        @DefaultValue("25000.00") @NotNull BigDecimal adjusterApprovalLimit,
        @NotNull MemberService memberService) {

    public record MemberService(
            @NotNull URI baseUrl,
            @DefaultValue("PT2S") Duration connectTimeout,
            @DefaultValue("PT2S") Duration readTimeout) {
    }
}
