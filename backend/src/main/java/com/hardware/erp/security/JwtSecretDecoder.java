package com.hardware.erp.security;

import io.jsonwebtoken.io.Decoders;

/**
 * Decodes a base64 HMAC signing key, failing with a message that names the
 * variable and the reason.
 *
 * Without this, a mistyped or mis-pasted secret surfaces as a bare
 * {@code DecodingException: Illegal base64 character: '\'} thrown from a
 * constructor and wrapped in nine layers of Spring bean-creation frames, with
 * no mention of JWT_SECRET anywhere in the message. That cost a production
 * deploy on 2026-09-05: the configured value had a backslash in it, which
 * base64 has no such character, and the log named only 'securityConfig' and
 * 'jwtService'.
 *
 * A standalone utility rather than a method on {@link JwtService}, because
 * PlatformAdminJwtService documents that it deliberately does NOT reuse
 * JwtService - the two keep independent SecretKey instances so a tenant token
 * can never parse as a platform-admin one (CR-054). Sharing a pure function
 * that touches no key material and holds no state does not weaken that
 * separation; sharing the service would.
 */
public final class JwtSecretDecoder {

    /** HS256 requires at least 256 bits of key material. */
    public static final int MIN_SECRET_BYTES = 32;

    private JwtSecretDecoder() {
    }

    /**
     * @param secret       the configured base64 value
     * @param propertyName the Spring property, for the error message
     * @param envVarName   the environment variable, for the error message
     * @return the decoded key, guaranteed at least {@value #MIN_SECRET_BYTES} bytes
     */
    public static byte[] decode(String secret, String propertyName, String envVarName) {
        // Trimmed because a copy-paste routinely carries a trailing newline or
        // space, and that alone would otherwise be fatal.
        String trimmed = secret == null ? "" : secret.trim();

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(trimmed);
        } catch (RuntimeException e) {
            throw new IllegalStateException("""

                    =====================================================
                     REFUSING TO START
                     %s (env %s) is not valid base64: %s

                     Base64 contains only A-Z a-z 0-9 + / and =. A backslash,
                     space or quote usually means the value was escaped,
                     wrapped or truncated on its way into the environment -
                     re-copy it exactly as generated.

                     Generate a fresh one:
                         openssl rand -base64 32      (Git Bash)
                         .\\scripts\\new-secret.ps1     (PowerShell)
                    ====================================================="""
                    .formatted(propertyName, envVarName, e.getMessage()), e);
        }

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("""

                    =====================================================
                     REFUSING TO START
                     %s (env %s) decoded to %d bytes; HS256 needs at least
                     %d bytes of key material.

                     Generate a fresh one:
                         openssl rand -base64 32      (Git Bash)
                         .\\scripts\\new-secret.ps1     (PowerShell)
                    ====================================================="""
                    .formatted(propertyName, envVarName, keyBytes.length, MIN_SECRET_BYTES));
        }
        return keyBytes;
    }
}
