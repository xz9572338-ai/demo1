package com.company.openplatform.identity.domain;

public final class LoginLockedException extends RuntimeException {
    private final long retryAfter;
    private final boolean ipLimited;
    public LoginLockedException(long retryAfter, boolean ipLimited) {
        this.retryAfter = Math.max(1, retryAfter); this.ipLimited = ipLimited;
    }
    public long retryAfter() { return retryAfter; }
    public boolean ipLimited() { return ipLimited; }
}
