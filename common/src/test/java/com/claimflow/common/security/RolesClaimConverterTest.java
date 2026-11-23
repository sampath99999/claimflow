package com.claimflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RolesClaimConverterTest {

    private final RolesClaimConverter converter = new RolesClaimConverter();

    @Test
    void mapsRolesClaimToPrefixedAuthorities() {
        Jwt jwt = jwt(List.of("ADJUSTER"), null);

        var token = converter.convert(jwt);

        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADJUSTER");
        assertThat(token.getName()).isEqualTo("ava");
    }

    @Test
    void tokenWithoutRolesHasNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("x")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }

    @Test
    void currentUserExposesMemberScope() {
        UUID memberId = UUID.randomUUID();
        var token = converter.convert(jwt(List.of("MEMBER"), memberId.toString()));

        CurrentUser user = CurrentUser.from(token);

        assertThat(user.isMember()).isTrue();
        assertThat(user.canAccessMember(memberId)).isTrue();
        assertThat(user.canAccessMember(UUID.randomUUID())).isFalse();
    }

    @Test
    void staffCanAccessAnyMember() {
        var user = CurrentUser.from(converter.convert(jwt(List.of("ADMIN"), null)));

        assertThat(user.isStaff()).isTrue();
        assertThat(user.memberId()).isEmpty();
        assertThat(user.canAccessMember(UUID.randomUUID())).isTrue();
    }

    private static Jwt jwt(List<String> roles, String memberId) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("ava")
                .claim(Roles.CLAIM_ROLES, roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (memberId != null) {
            builder.claim(Roles.CLAIM_MEMBER_ID, memberId);
        }
        return builder.build();
    }
}
