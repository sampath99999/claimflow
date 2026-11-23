package com.claimflow.member.service;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
