package com.company.openplatform.identity.domain;

public final class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException() {
        super("账号已存在");
    }
}
