package com.claimflow.claims.client;

public class EligibilityUnavailableException extends RuntimeException {

    public EligibilityUnavailableException(Throwable cause) {
        super("Eligibility check is unavailable, try again shortly", cause);
    }
}
