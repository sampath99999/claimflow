package com.claimflow.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.claimflow.gateway.config.GatewayProperties;
import com.claimflow.gateway.config.GatewayProperties.UserAccount;
import com.claimflow.gateway.config.JwtKeyConfig;
import com.nimbusds.jose.jwk.RSAKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class TokenServiceTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private TokenService service;
    private RSAKey key;

    @BeforeEach
    void setUp() throws Exception {
        var properties = new GatewayProperties(
                new GatewayProperties.Services(URI.create("http://m"), URI.create("http://c"), URI.create("http://a")),
                new GatewayProperties.Jwt("http://test-issuer", Duration.ofMinutes(30), null, null),
                List.of(),
                List.of(
                        new UserAccount("maria", encoder.encode("secret-1"), "MEMBER", "Maria G", MEMBER_ID),
                        new UserAccount("ava", encoder.encode("secret-2"), "ADJUSTER", "Ava T", null)));
        var keys = new JwtKeyConfig();
        key = keys.signingKey(properties);
        service = new TokenService(new UserDirectory(properties), encoder, keys.jwtEncoder(key), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuesSignedTokenWithRolesAndMemberId() throws Exception {
        var token = service.login("maria", "secret-1").orElseThrow();

        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("maria");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MEMBER");
        assertThat(jwt.getClaimAsString("memberId")).isEqualTo(MEMBER_ID.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("http://test-issuer");
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void usernameIsCaseInsensitive() {
        assertThat(service.login("AVA", "secret-2")).isPresent();
    }

    @Test
    void staffTokenHasNoMemberId() throws Exception {
        var token = service.login("ava", "secret-2").orElseThrow();

        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token.value());
        assertThat(jwt.hasClaim("memberId")).isFalse();
    }

    @Test
    void rejectsWrongPasswordAndUnknownUser() {
        assertThat(service.login("maria", "wrong")).isEmpty();
        assertThat(service.login("nobody", "secret-1")).isEmpty();
    }
}
