package com.hardware.erp.security.captcha;

import com.hardware.erp.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These cover the fail-safe half of the contract - the half that decides
 * whether a misconfiguration locks every user out of a working system.
 *
 * The verify-a-real-token path is not covered here: it needs an HTTP call to
 * Cloudflare, which belongs in an integration test with a stub server, and
 * Testcontainers/Docker is unavailable in this environment (BUG-ENV-002).
 */
class TurnstileCaptchaServiceTest {

    private static TurnstileCaptchaService serviceWith(boolean enabled, String siteKey, String secretKey) {
        return new TurnstileCaptchaService(
                new CaptchaProperties(enabled, secretKey, siteKey, null));
    }

    @Test
    @DisplayName("disabled: no token needed, nothing is verified")
    void disabledSkipsVerification() {
        TurnstileCaptchaService service = serviceWith(false, "site", "secret");

        assertThat(service.active()).isFalse();
        assertThat(service.siteKey()).isNull();
        assertThatCode(() -> service.verify(null, "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enabled but keys missing: treated as disabled, never blocks sign-in")
    void enabledWithoutKeysIsInactive() {
        assertThat(serviceWith(true, "", "secret").active()).isFalse();
        assertThat(serviceWith(true, "site", "").active()).isFalse();
        assertThat(serviceWith(true, null, null).active()).isFalse();

        // The important one: switching the flag on without supplying keys must
        // not start rejecting logins.
        assertThatCode(() -> serviceWith(true, "site", "").verify(null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("active and token missing: rejected as a bad request, not a server error")
    void activeRejectsMissingToken() {
        TurnstileCaptchaService service = serviceWith(true, "site", "secret");

        assertThat(service.active()).isTrue();
        assertThat(service.siteKey()).isEqualTo("site");

        assertThatThrownBy(() -> service.verify("   ", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("CAPTCHA_FAILED");
                });
    }

    @Test
    @DisplayName("verify URL defaults to Cloudflare when unset")
    void verifyUrlHasADefault() {
        assertThat(new CaptchaProperties(true, "s", "k", null).verifyUrl())
                .isEqualTo("https://challenges.cloudflare.com/turnstile/v0/siteverify");
        assertThat(new CaptchaProperties(true, "s", "k", "  ").verifyUrl())
                .isEqualTo("https://challenges.cloudflare.com/turnstile/v0/siteverify");
    }

    @Test
    @DisplayName("an unreachable verifier fails closed, and says so as a 503")
    void unreachableVerifierFailsClosed() {
        // Points at a port nothing is listening on, so the call cannot succeed.
        TurnstileCaptchaService service = new TurnstileCaptchaService(
                new CaptchaProperties(true, "secret", "site", "http://127.0.0.1:1/verify"));

        assertThatThrownBy(() -> service.verify("some-token", "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    // Not 400: this is not the user's mistake, and telling them
                    // it is would have them retry the challenge forever.
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(be.getCode()).isEqualTo("CAPTCHA_UNAVAILABLE");
                });
    }
}
