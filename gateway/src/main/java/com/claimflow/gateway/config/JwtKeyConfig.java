package com.claimflow.gateway.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public RSAKey signingKey(GatewayProperties properties) throws Exception {
        GatewayProperties.Jwt jwt = properties.jwt();
        KeyPair keyPair;
        if (jwt.privateKey() != null && jwt.publicKey() != null) {
            keyPair = new KeyPair(readPublic(jwt.publicKey()), readPrivate(jwt.privateKey()));
        } else {
            log.warn("No JWT signing key configured, generating an ephemeral RSA key. Do not use this outside local development.");
            keyPair = generate();
        }
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        return new RSAKey.Builder(key)
                .keyID(key.computeThumbprint().toString())
                .build();
    }

    @Bean
    JWKSet publicJwkSet(RSAKey signingKey) {
        return new JWKSet(signingKey.toPublicJWK());
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(RSAKey signingKey, GatewayProperties properties) throws Exception {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    private static RSAPublicKey readPublic(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        }
    }

    private static RSAPrivateKey readPrivate(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(in);
        }
    }

    private static KeyPair generate() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
