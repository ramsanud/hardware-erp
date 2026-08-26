package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse")
public record LoginResponse(

        @Schema(description = "JWT access token. Keep in memory only, never localStorage.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJoYXJkd2FyZS1lcnAiLCJzdWIiOiIxIn0.abc123")
        String accessToken,

        @Schema(description = "Opaque refresh token. Null when the cookie transport is active.",
                example = "null")
        String refreshToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "900")
        long expiresInSeconds,

        @Schema(description = "Force the password change screen before anything else",
                example = "false")
        boolean mustChangePassword,

        UserResponse user
) {}
