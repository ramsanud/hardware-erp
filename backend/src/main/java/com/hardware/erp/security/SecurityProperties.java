package com.hardware.erp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(

        /**
         * COOKIE (default): refresh token travels in an HttpOnly, Secure,
         * SameSite=Strict cookie scoped to /api/v1/auth. JavaScript cannot read
         * it, so an XSS bug cannot exfiltrate the long-lived credential.
         *
         * JSON: refresh token returned in the response body. Only for
         * non-browser clients (a future mobile app). Documented as less safe.
         */
        RefreshTokenTransport refreshTokenTransport,

        String cookieName,

        /** false only for local http development. */
        boolean cookieSecure,

        /**
         * CR-060. Whether a correct password issues an MFA challenge (true, the
         * default and CR-058's behaviour) or a session directly (false).
         *
         * This exists so MFA can be switched off during development without
         * deleting the CR-058 implementation, which stays intact and is
         * re-enabled by removing MFA_REQUIRED=false from the environment.
         *
         * Defaults to true deliberately: an installation that says nothing
         * about MFA gets the secure behaviour, and turning it off has to be a
         * written-down decision rather than an omission. DeploymentModeGuard
         * prints "mfa: DISABLED" in the startup banner whenever it is false, so
         * it can never be off without somebody being told on every boot.
         */
        boolean mfaRequired,

        List<String> allowedOrigins
) {
    public enum RefreshTokenTransport { COOKIE, JSON }
}
