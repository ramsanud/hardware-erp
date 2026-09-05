package com.hardware.erp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Every field is a wrapper type with a default applied below, never a
 * primitive, and that is deliberate.
 *
 * Spring's <code>${VAR:default}</code> only falls back when VAR is ABSENT. A
 * variable that exists but is empty - the normal result of adding a key in a
 * hosting dashboard and leaving the value box blank, or of an env file line
 * like <code>MFA_REQUIRED=</code> - resolves to the empty string instead. The
 * binder turns that into null, and null will not bind to a primitive boolean
 * or to an enum, so the whole application fails to start with
 *
 *   Could not bind properties to 'SecurityProperties' : prefix=app.security
 *
 * which names neither the offending key nor the reason. That cost a
 * production deploy on 2026-09-05. Defaulting here means a blank value is
 * simply treated as unset, which is what whoever left it blank meant.
 *
 * Every default below is the SECURE choice, so a blank value can never
 * silently weaken the application - only fail to strengthen it.
 */
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
        Boolean cookieSecure,

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
        Boolean mfaRequired,

        List<String> allowedOrigins
) {
    public SecurityProperties {
        if (refreshTokenTransport == null) {
            refreshTokenTransport = RefreshTokenTransport.COOKIE;
        }
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "erp_refresh_token";
        }
        // Secure-by-default on both: a blank value must never be the reason a
        // 7-day credential crosses plain HTTP, or the reason a second factor
        // stops being asked for.
        if (cookieSecure == null) {
            cookieSecure = Boolean.TRUE;
        }
        if (mfaRequired == null) {
            mfaRequired = Boolean.TRUE;
        }
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }

    public enum RefreshTokenTransport { COOKIE, JSON }
}
