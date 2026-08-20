package com.company.openplatform.shared.security;

public final class RegistrationRateLimitException extends RuntimeException {
    public RegistrationRateLimitException() {
        super("Registration business rate limit exceeded");
    }
}
