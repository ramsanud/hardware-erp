package com.hardware.erp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These exist because of a real production failure on 2026-09-05. A Render
 * deployment died with
 *
 *   Caused by: io.jsonwebtoken.io.DecodingException: Illegal base64 character: '\'
 *       at com.hardware.erp.security.JwtService.&lt;init&gt;(JwtService.java:39)
 *
 * buried under nine layers of Spring bean-creation frames whose visible
 * message named only 'securityConfig' and 'jwtService'. Nothing said
 * JWT_SECRET, and nothing said what a valid value looks like.
 */
class JwtSecretDecoderTest {

    private static final String VALID = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    @DisplayName("a valid 32-byte base64 secret decodes")
    void validSecretDecodes() {
        assertThat(JwtSecretDecoder.decode(VALID, "app.jwt.secret", "JWT_SECRET")).hasSize(32);
    }

    @Test
    @DisplayName("the failure names the environment variable, not just the bean")
    void errorNamesTheEnvironmentVariable() {
        // The whole point: the operator must learn WHICH variable to fix.
        assertThatThrownBy(() -> JwtSecretDecoder.decode("abc\\def", "app.jwt.secret", "JWT_SECRET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("app.jwt.secret")
                .hasMessageContaining("not valid base64");
    }

    @Test
    @DisplayName("the platform-admin key reports its own variable, never the tenant one")
    void errorNamesThePlatformAdminVariable() {
        assertThatThrownBy(() -> JwtSecretDecoder.decode(
                "abc\\def", "app.platform-admin.jwt.secret", "PLATFORM_ADMIN_JWT_SECRET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_ADMIN_JWT_SECRET")
                .hasMessageNotContaining("env JWT_SECRET");
    }

    @Test
    @DisplayName("a secret that is valid base64 but too short is refused with its actual length")
    void tooShortIsRefused() {
        String tooShort = Base64.getEncoder().encodeToString("only-sixteen-byte".getBytes());

        assertThatThrownBy(() -> JwtSecretDecoder.decode(tooShort, "app.jwt.secret", "JWT_SECRET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HS256")
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("surrounding whitespace is tolerated - a copy-paste newline must not be fatal")
    void whitespaceIsTrimmed() {
        assertThatCode(() -> JwtSecretDecoder.decode("  " + VALID + "\n", "app.jwt.secret", "JWT_SECRET"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null and blank are refused as clearly as garbage")
    void nullAndBlankAreRefused() {
        assertThatThrownBy(() -> JwtSecretDecoder.decode(null, "app.jwt.secret", "JWT_SECRET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> JwtSecretDecoder.decode("   ", "app.jwt.secret", "JWT_SECRET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
