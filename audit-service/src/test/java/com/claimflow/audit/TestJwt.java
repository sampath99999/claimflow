package com.claimflow.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

public final class TestJwt {

    private TestJwt() {
    }

    public static JwtRequestPostProcessor admin() {
        return as("admin", "ADMIN", null);
    }

    public static JwtRequestPostProcessor adjuster() {
        return as("ava", "ADJUSTER", null);
    }

    public static JwtRequestPostProcessor member(UUID memberId) {
        return as("maria", "MEMBER", memberId);
    }

    private static JwtRequestPostProcessor as(String username, String role, UUID memberId) {
        return jwt()
                .jwt(token -> {
                    token.subject(username).claim("roles", List.of(role));
                    if (memberId != null) {
                        token.claim("memberId", memberId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
