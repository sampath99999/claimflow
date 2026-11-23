package com.claimflow.gateway.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.claimflow.gateway.config.GatewayProperties;
import com.claimflow.gateway.config.GatewayProperties.UserAccount;

import org.junit.jupiter.api.Test;

class UserDirectoryTest {

    @Test
    void memberAccountWithoutMemberIdFailsFast() {
        var properties = properties(new UserAccount("x", "hash", "MEMBER", "X", null));

        assertThatThrownBy(() -> new UserDirectory(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("memberId");
    }

    @Test
    void unknownRoleFailsFast() {
        var properties = properties(new UserAccount("x", "hash", "SUPERUSER", "X", null));

        assertThatThrownBy(() -> new UserDirectory(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPERUSER");
    }

    private static GatewayProperties properties(UserAccount account) {
        return new GatewayProperties(
                new GatewayProperties.Services(URI.create("http://m"), URI.create("http://c"), URI.create("http://a")),
                new GatewayProperties.Jwt("issuer", Duration.ofHours(1), null, null),
                List.of(),
                List.of(account));
    }
}
