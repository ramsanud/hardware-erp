package com.hardware.erp.common.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field-level encryption for values that must be stored
 * encrypted but genuinely retrieved in plaintext later (unlike a password
 * hash) - today only supplier.bank_account_no (CR-018).
 *
 * Not a Spring bean deliberately: the key comes straight from the
 * APP_ENCRYPTION_KEY environment variable via System.getenv(), so a JPA
 * AttributeConverter (which Hibernate instantiates itself, not Spring) can
 * use this with no bean-container wiring.
 *
 * Graceful degradation, matching every other optional integration in this
 * codebase (mail, AI, WhatsApp): if the key is unset, encrypt() returns the
 * plaintext unchanged instead of throwing - a fresh dev environment keeps
 * working exactly as before this feature existed, rather than every
 * supplier save failing with a 500 the first time someone forgets to set an
 * env var. decrypt() already has to tolerate un-prefixed legacy plaintext
 * (see isEncrypted), so the two behaviors are consistent with each other.
 */
public final class FieldEncryptor {

    private static final String MARKER = "ENC:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private FieldEncryptor() {
    }

    public static boolean isConfigured() {
        return resolveKey() != null;
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(MARKER);
    }

    /** Returns the plaintext unchanged if no key is configured - see class comment. */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        byte[] key = resolveKey();
        if (key == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return MARKER + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /**
     * A value with no ENC: prefix is legacy plaintext (or encryption was
     * never configured) and is returned as-is - never attempted as
     * ciphertext, which would just throw on garbage input.
     */
    public static String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        byte[] key = resolveKey();
        if (key == null) {
            throw new IllegalStateException(
                    "Stored value is encrypted but APP_ENCRYPTION_KEY is not configured - cannot decrypt");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(MARKER.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field decryption failed", e);
        }
    }

    /**
     * Base64-encoded 32-byte (256-bit) key, e.g. `openssl rand -base64 32`.
     * Null if unset or the wrong length. Checks the system property first -
     * env vars can't be set at runtime, so tests use
     * System.setProperty/clearProperty to exercise both the configured and
     * unconfigured paths deterministically; real deployments only ever set
     * the environment variable.
     */
    private static byte[] resolveKey() {
        String encoded = System.getProperty("APP_ENCRYPTION_KEY");
        if (encoded == null || encoded.isBlank()) {
            encoded = System.getenv("APP_ENCRYPTION_KEY");
        }
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        byte[] key = Base64.getDecoder().decode(encoded.trim());
        return key.length == 32 ? key : null;
    }
}
