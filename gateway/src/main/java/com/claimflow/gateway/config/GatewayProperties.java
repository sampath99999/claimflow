package com.claimflow.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "claimflow")
public record GatewayProperties(
        @Valid @NotNull Services services,
        @Valid @NotNull Jwt jwt,
        @DefaultValue List<String> corsAllowedOrigins,
        @Valid @DefaultValue List<UserAccount> users) {

    public record Services(@NotNull URI member, @NotNull URI claims, @NotNull URI audit) {
    }

    /**
     * When privateKey/publicKey are not set an RSA key pair is generated at startup,
     * which is fine for local runs but invalidates tokens on every restart.
     */
    public record Jwt(
            @NotBlank String issuer,
            @DefaultValue("PT1H") Duration ttl,
            Resource privateKey,
            Resource publicKey) {
    }

    public record UserAccount(
            @NotBlank String username,
            @NotBlank String passwordHash,
            @NotBlank String role,
            @NotBlank String displayName,
            UUID memberId) {
    }
}
