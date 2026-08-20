package com.company.openplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.openplatform.identity.infrastructure.AesGcmMobileProtector;
import org.junit.jupiter.api.Test;

class RegistrationSecurityUnitTest {
    @Test
    void encryptsWithRandomAuthenticatedEnvelopeAndStableFingerprint() {
        var protector = new AesGcmMobileProtector(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "test-v1");
        var first = protector.protect("13812345678");
        var second = protector.protect("13812345678");
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext()).doesNotContain("13812345678");
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint()).hasSize(64);
        assertThat(first.keyId()).isEqualTo("test-v1");
    }

    @Test
    void failsClosedWithoutA256Key() {
        assertThatThrownBy(() -> new AesGcmMobileProtector("", "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密钥");
    }

    @Test
    void rejectsInvalidKeyIdAtStartup() {
        assertThatThrownBy(() -> new AesGcmMobileProtector(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标识");
    }
}
