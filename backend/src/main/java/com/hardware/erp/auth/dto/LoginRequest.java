package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Sign in with mobile number or email")
public record LoginRequest(

        @Schema(description = "Mobile number or email address", example = "9876543210")
        @NotBlank(message = "Mobile number or email is required")
        @Size(max = 255, message = "Mobile number or email is too long")
        String identifier,

        @Schema(description = "Account password", example = "Owner@2026")
        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password is too long")
        String password,

        /**
         * Optional at the DTO level on purpose: whether a token is actually
         * required is a runtime decision (app.captcha.enabled plus real keys),
         * not a compile-time one. A @NotBlank here would break every existing
         * client the moment CAPTCHA was switched on, and would reject logins
         * on installs that never configure it at all.
         */
        @Schema(description = "Cloudflare Turnstile token, when the security check is enabled")
        @Size(max = 4096, message = "Security check token is too long")
        String captchaToken
) {
    /**
     * Identifier and password only, for callers that predate the security
     * check - tests, and any client on an install where CAPTCHA is off. Same
     * convenience-constructor pattern InvoiceRequest already uses.
     */
    public LoginRequest(String identifier, String password) {
        this(identifier, password, null);
    }
}
