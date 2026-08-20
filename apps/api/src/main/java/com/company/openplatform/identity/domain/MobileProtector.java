package com.company.openplatform.identity.domain;

public interface MobileProtector {
    EncryptedMobile protect(String normalizedMobile);
}
