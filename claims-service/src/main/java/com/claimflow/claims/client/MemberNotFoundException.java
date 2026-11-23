package com.claimflow.claims.client;

import java.util.UUID;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(UUID memberId) {
        super("Member " + memberId + " not found");
    }
}
