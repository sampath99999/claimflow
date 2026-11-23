package com.claimflow.gateway.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.claimflow.common.security.Roles;
import com.claimflow.gateway.config.GatewayProperties;
import com.claimflow.gateway.config.GatewayProperties.UserAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    // Compared against when the username is unknown so both paths cost one BCrypt check.
    private static final String DUMMY_HASH = "$2a$10$f3dP0qLuT.guTxgUsu1mSu/f21mtE9Z9Bp/3/JYXgOyBSXBvswZ.2";

    private final UserDirectory users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final GatewayProperties.Jwt jwtProperties;
    private final Clock clock;

    public TokenService(UserDirectory users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                        GatewayProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = properties.jwt();
        this.clock = clock;
    }

    public Optional<IssuedToken> login(String username, String password) {
        Optional<UserAccount> account = users.find(username);
        String hash = account.map(UserAccount::passwordHash).orElse(DUMMY_HASH);
        boolean matches = passwordEncoder.matches(password, hash);
        if (account.isEmpty() || !matches) {
            log.info("Failed login for username={}", username);
            return Optional.empty();
        }
        return Optional.of(issue(account.get()));
    }

    IssuedToken issue(UserAccount account) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(account.username())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(Roles.CLAIM_ROLES, List.of(account.role()))
                .claim(Roles.CLAIM_NAME, account.displayName());
        if (account.memberId() != null) {
            claims.claim(Roles.CLAIM_MEMBER_ID, account.memberId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt, account);
    }

    public record IssuedToken(String value, Instant expiresAt, UserAccount account) {
    }
}
