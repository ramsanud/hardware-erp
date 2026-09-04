package com.hardware.erp.platformadmin.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Its own secret and issuer, structurally separate from app.jwt (JwtProperties).
 * Even if one signing key were somehow compromised, a token signed with it
 * could never be accepted as the other kind of session - the parser checks
 * both the signature and the issuer claim.
 */
@Validated
@ConfigurationProperties(prefix = "app.platform-admin.jwt")
public record PlatformAdminJwtProperties(

        @NotBlank(message = "app.platform-admin.jwt.secret must be set (env PLATFORM_ADMIN_JWT_SECRET)")
        String secret,

        @NotBlank
        String issuer,

        @Min(value = 1, message = "Access token lifetime must be at least 1 minute")
        long accessTokenMinutes,

        @Min(value = 1, message = "Refresh token lifetime must be at least 1 day")
        long refreshTokenDays,

        @Min(value = 1, message = "MFA challenge token lifetime must be at least 1 minute")
        long mfaTokenMinutes
) {}
