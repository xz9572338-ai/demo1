package com.company.openplatform.gateway.security;

public final class OpenApiFailure extends RuntimeException {
    private final int status; private final String code; private final boolean retryable; private final Integer retryAfter;
    public OpenApiFailure(int status, String code, boolean retryable) { this(status, code, retryable, null); }
    public OpenApiFailure(int status, String code, boolean retryable, Integer retryAfter) {
        super(code); this.status=status; this.code=code; this.retryable=retryable; this.retryAfter=retryAfter;
    }
    public int status(){return status;} public String code(){return code;} public boolean retryable(){return retryable;}
    public Integer retryAfter(){return retryAfter;}
}
