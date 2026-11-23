package com.claimflow.claims.service;

import java.util.UUID;

public class StaleClaimException extends RuntimeException {

    private final long currentVersion;

    public StaleClaimException(UUID id, long expectedVersion, long currentVersion) {
        super("Claim " + id + " is at version " + currentVersion + " but the request was based on version " + expectedVersion);
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
