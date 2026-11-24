package com.claimflow.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class JwtKeyConfigTest {

    @TempDir
    Path dir;

    @Test
    void loadsConfiguredPemKeysSoAllReplicasShareOneKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Path privatePem = write("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicPem = write("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        var config = new JwtKeyConfig();
        var first = config.signingKey(properties(privatePem, publicPem));
        var second = config.signingKey(properties(privatePem, publicPem));

        assertThat(first.toRSAPublicKey()).isEqualTo(pair.getPublic());
        assertThat(first.getKeyID()).isEqualTo(second.getKeyID());
    }

    @Test
    void generatesDistinctKeysWhenNoneConfigured() throws Exception {
        var config = new JwtKeyConfig();

        assertThat(config.signingKey(properties(null, null)).getKeyID())
                .isNotEqualTo(config.signingKey(properties(null, null)).getKeyID());
    }

    private Path write(String name, String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return Files.writeString(dir.resolve(name), "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }

    private static GatewayProperties properties(Path privatePem, Path publicPem) {
        return new GatewayProperties(
                new GatewayProperties.Services(URI.create("http://m"), URI.create("http://c"), URI.create("http://a")),
                new GatewayProperties.Jwt("issuer", Duration.ofHours(1),
                        privatePem == null ? null : new FileSystemResource(privatePem),
                        publicPem == null ? null : new FileSystemResource(publicPem)),
                List.of(),
                List.of());
    }
}
