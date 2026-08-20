package com.company.openplatform.identity.domain;

public record EncryptedMobile(String ciphertext, String keyId, String fingerprint) {}
