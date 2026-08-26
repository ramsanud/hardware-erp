package com.hardware.erp.security.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Turnstile settings.
 *
 * Disabled by default and treated as disabled whenever `secretKey` is blank -
 * the same "unconfigured means skip, never crash" convention SmtpMailService
 * and the AI clients already follow. A CAPTCHA that hard-fails when its keys
 * are missing would lock every user out of a working system, which is a worse
 * outcome than the automated sign-in attempts it exists to stop.
 */
@ConfigurationProperties(prefix = "app.captcha")
public record CaptchaProperties(

        boolean enabled,

        /** Server-side secret. Never sent to the browser. */
        String secretKey,

        /** Public key the widget needs. Served to the browser by /v1/auth/captcha-config. */
        String siteKey,

        String verifyUrl
) {
    public CaptchaProperties {
        if (verifyUrl == null || verifyUrl.isBlank()) {
            verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        }
    }

    /** Enabled AND actually usable. Both keys are required for a real verification. */
    public boolean active() {
        return enabled
                && secretKey != null && !secretKey.isBlank()
                && siteKey != null && !siteKey.isBlank();
    }
}
