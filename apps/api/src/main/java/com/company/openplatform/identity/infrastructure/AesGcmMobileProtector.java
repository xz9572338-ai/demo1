package com.company.openplatform.identity.infrastructure;

import com.company.openplatform.identity.domain.EncryptedMobile;
import com.company.openplatform.identity.domain.MobileProtector;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class AesGcmMobileProtector implements MobileProtector {
    private static final int NONCE_BYTES = 12;
    private final byte[] encryptionKey;
    private final byte[] fingerprintKey;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public AesGcmMobileProtector(
            @Value("${open-platform.security.phone-key:}") String encodedKey,
            @Value("${open-platform.security.phone-key-id:local-v1}") String keyId) {
        byte[] masterKey;
        try {
            masterKey = encodedKey == null || encodedKey.isBlank()
                    ? new byte[0] : Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("手机号加密密钥必须是有效 Base64", exception);
        }
        if (masterKey.length != 32) {
            throw new IllegalArgumentException("手机号加密密钥必须为 256 位");
        }
        if (keyId == null || keyId.isBlank() || keyId.length() > 64) {
            throw new IllegalArgumentException("手机号加密密钥标识长度必须为 1-64");
        }
        this.encryptionKey = derive(masterKey, "phone-encryption");
        this.fingerprintKey = derive(masterKey, "phone-fingerprint");
        this.keyId = keyId;
    }

    @Override
    public EncryptedMobile protect(String normalizedMobile) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(normalizedMobile.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
            String fingerprint = bytesToHex(mac.doFinal(normalizedMobile.getBytes(StandardCharsets.UTF_8)));
            return new EncryptedMobile(Base64.getEncoder().encodeToString(envelope), keyId, fingerprint);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("手机号保护失败", exception);
        }
    }

    private static byte[] derive(byte[] masterKey, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("手机号密钥派生失败", exception);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
