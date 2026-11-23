package com.claimflow.claims.web;

final class ETags {

    private ETags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    static long requireVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match must be an ETag returned by this API, got " + ifMatch);
        }
    }

    static class PreconditionRequiredException extends RuntimeException {
        PreconditionRequiredException() {
            super("Send the claim's ETag in an If-Match header so concurrent edits are detected");
        }
    }
}
