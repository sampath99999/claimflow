package com.claimflow.claims.service;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(UUID id) {
        super("Claim " + id + " not found");
    }
}
