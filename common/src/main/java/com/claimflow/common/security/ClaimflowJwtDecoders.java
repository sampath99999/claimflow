package com.claimflow.common.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class ClaimflowJwtDecoders {

    private ClaimflowJwtDecoders() {
    }

    /**
     * The JWK set is fetched lazily on the first request, so services can start before the gateway.
     */
    public static JwtDecoder fromJwkSet(String jwkSetUri, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
