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

        List<String> allowedOrigins
) {
    public enum RefreshTokenTransport { COOKIE, JSON }
}
