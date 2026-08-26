package com.hardware.erp.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @AfterEach
    void clearKey() {
        System.clearProperty("APP_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("a value round-trips through encrypt then decrypt")
    void roundTrips() {
        System.setProperty("APP_ENCRYPTION_KEY", TEST_KEY);

        String ciphertext = FieldEncryptor.encrypt("50100123456789");

        assertThat(FieldEncryptor.isEncrypted(ciphertext)).isTrue();
        assertThat(ciphertext).doesNotContain("50100123456789");
        assertThat(FieldEncryptor.decrypt(ciphertext)).isEqualTo("50100123456789");
    }

    @Test
    @DisplayName("two encryptions of the same plaintext produce different ciphertext (random IV)")
    void ciphertextIsNotDeterministic() {
        System.setProperty("APP_ENCRYPTION_KEY", TEST_KEY);

        String first = FieldEncryptor.encrypt("50100123456789");
        String second = FieldEncryptor.encrypt("50100123456789");

        assertThat(first).isNotEqualTo(second);
        assertThat(FieldEncryptor.decrypt(first)).isEqualTo(FieldEncryptor.decrypt(second));
    }

    @Test
    @DisplayName("legacy plaintext (no ENC: prefix) passes through decrypt unchanged")
    void legacyPlaintextPassesThrough() {
        System.setProperty("APP_ENCRYPTION_KEY", TEST_KEY);

        assertThat(FieldEncryptor.decrypt("50100123456789")).isEqualTo("50100123456789");
        assertThat(FieldEncryptor.isEncrypted("50100123456789")).isFalse();
    }

    @Test
    @DisplayName("with no key configured, encrypt is a no-op passthrough rather than throwing")
    void gracefulDegradationWhenUnconfigured() {
        System.clearProperty("APP_ENCRYPTION_KEY");

        assertThat(FieldEncryptor.isConfigured()).isFalse();
        assertThat(FieldEncryptor.encrypt("50100123456789")).isEqualTo("50100123456789");
    }

    @Test
    @DisplayName("a null or blank value is never touched, configured or not")
    void nullAndBlankUntouched() {
        System.setProperty("APP_ENCRYPTION_KEY", TEST_KEY);

        assertThat(FieldEncryptor.encrypt(null)).isNull();
        assertThat(FieldEncryptor.encrypt("")).isEmpty();
        assertThat(FieldEncryptor.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("decrypting an ENC:-prefixed value with no key configured fails clearly, not silently")
    void decryptWithoutKeyFailsClearly() {
        System.setProperty("APP_ENCRYPTION_KEY", TEST_KEY);
        String ciphertext = FieldEncryptor.encrypt("50100123456789");
        System.clearProperty("APP_ENCRYPTION_KEY");

        assertThatThrownBy(() -> FieldEncryptor.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a key of the wrong length is treated as not configured")
    void wrongLengthKeyIsRejected() {
        byte[] wrongLength = new byte[16];
        new Random(1).nextBytes(wrongLength);
        System.setProperty("APP_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(wrongLength));

        assertThat(FieldEncryptor.isConfigured()).isFalse();
    }
}
