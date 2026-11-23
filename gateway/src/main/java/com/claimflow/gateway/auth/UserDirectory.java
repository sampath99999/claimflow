package com.claimflow.gateway.auth;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.claimflow.common.security.Roles;
import com.claimflow.gateway.config.GatewayProperties;
import com.claimflow.gateway.config.GatewayProperties.UserAccount;

import org.springframework.stereotype.Component;

/**
 * Login accounts come from configuration (BCrypt hashes only). A real deployment would put an
 * identity provider such as Entra ID in front instead; the downstream services would not change.
 */
@Component
public class UserDirectory {

    private static final Set<String> KNOWN_ROLES = Set.of(Roles.MEMBER, Roles.ADJUSTER, Roles.ADMIN);

    private final Map<String, UserAccount> accounts;

    public UserDirectory(GatewayProperties properties) {
        properties.users().forEach(UserDirectory::check);
        this.accounts = properties.users().stream()
                .collect(Collectors.toUnmodifiableMap(u -> u.username().toLowerCase(), Function.identity()));
    }

    public Optional<UserAccount> find(String username) {
        return Optional.ofNullable(accounts.get(username.toLowerCase()));
    }

    private static void check(UserAccount account) {
        if (!KNOWN_ROLES.contains(account.role())) {
            throw new IllegalStateException("Unknown role " + account.role() + " for user " + account.username());
        }
        if (Roles.MEMBER.equals(account.role()) && account.memberId() == null) {
            throw new IllegalStateException("Member account " + account.username() + " needs a memberId");
        }
    }
}
