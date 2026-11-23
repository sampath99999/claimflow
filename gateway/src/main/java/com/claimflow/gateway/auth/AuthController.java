package com.claimflow.gateway.auth;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import com.claimflow.common.security.Roles;
import com.nimbusds.jose.jwk.JWKSet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class AuthController {

    private final TokenService tokenService;
    private final JWKSet publicJwkSet;
    private final Clock clock;

    public AuthController(TokenService tokenService, JWKSet publicJwkSet, Clock clock) {
        this.tokenService = tokenService;
        this.publicJwkSet = publicJwkSet;
        this.clock = clock;
    }

    @PostMapping("/auth/login")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        // BCrypt is deliberately slow, keep it off the event loop.
        return Mono.fromCallable(() -> tokenService.login(request.username(), request.password()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> result.orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")))
                .map(this::toResponse);
    }

    @GetMapping("/auth/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        String memberId = jwt.getClaimAsString(Roles.CLAIM_MEMBER_ID);
        return new UserView(
                jwt.getSubject(),
                jwt.getClaimAsString(Roles.CLAIM_NAME),
                jwt.getClaimAsStringList(Roles.CLAIM_ROLES).getFirst(),
                memberId == null ? null : UUID.fromString(memberId));
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = "application/json")
    public Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }

    private TokenResponse toResponse(TokenService.IssuedToken token) {
        var account = token.account();
        long expiresIn = token.expiresAt().getEpochSecond() - clock.instant().getEpochSecond();
        return new TokenResponse(token.value(), "Bearer", expiresIn,
                new UserView(account.username(), account.displayName(), account.role(), account.memberId()));
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserView user) {
    }

    public record UserView(String username, String displayName, String role, UUID memberId) {
    }
}
