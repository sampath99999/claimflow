package com.claimflow.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Read-only view of the authenticated caller, built from the JWT.
 */
public record CurrentUser(String username, String role, Optional<UUID> memberId, String bearerToken) {

    public static CurrentUser from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Expected a JWT authenticated request");
        }
        Jwt jwt = token.getToken();
        var roles = jwt.getClaimAsStringList(Roles.CLAIM_ROLES);
        String role = roles == null || roles.isEmpty() ? "" : roles.getFirst();
        String memberId = jwt.getClaimAsString(Roles.CLAIM_MEMBER_ID);
        return new CurrentUser(
                jwt.getSubject(),
                role,
                Optional.ofNullable(memberId).filter(s -> !s.isBlank()).map(UUID::fromString),
                jwt.getTokenValue());
    }

    public boolean isMember() {
        return Roles.MEMBER.equals(role);
    }

    public boolean isStaff() {
        return Roles.ADJUSTER.equals(role) || Roles.ADMIN.equals(role);
    }

    public boolean canAccessMember(UUID id) {
        return isStaff() || memberId.map(id::equals).orElse(false);
    }
}
