package com.company.openplatform.permission.application;
public class PermissionConflictException extends RuntimeException {
    private final String code;
    public PermissionConflictException(String code) { super(code); this.code=code; }
    public String code() { return code; }
}
