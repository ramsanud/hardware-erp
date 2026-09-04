package com.hardware.erp.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        /** Base64-encoded, must decode to >= 32 bytes. Generate: openssl rand -base64 32 */
        @NotBlank(message = "app.jwt.secret must be set (env JWT_SECRET)")
        String secret,

        @NotBlank
        String issuer,

        @Min(value = 1, message = "Access token lifetime must be at least 1 minute")
        long accessTokenMinutes,

        @Min(value = 1, message = "Refresh token lifetime must be at least 1 day")
        long refreshTokenDays,

        /** CR-058 - how long a "password check passed" MFA challenge token stays usable before the user must sign in again. */
        @Min(value = 1, message = "MFA token lifetime must be at least 1 minute")
        long mfaTokenMinutes
) {}
