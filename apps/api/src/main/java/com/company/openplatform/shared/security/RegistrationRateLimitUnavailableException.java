package com.company.openplatform.shared.security;

public final class RegistrationRateLimitUnavailableException extends RuntimeException {
    public RegistrationRateLimitUnavailableException(Throwable cause) {
        super("Registration rate limit unavailable", cause);
    }
}
