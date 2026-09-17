package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CR-078. Proof that the signed-in user just re-confirmed by email code.
 * Presented as {@code stepUpToken} to the sensitive endpoints that demand
 * it; each one verifies it independently and it expires like an MFA token.
 */
@Schema(name = "StepUpResponse")
public record StepUpResponse(
        @Schema(description = "Short-lived token proving the re-confirmation. Not a session token.")
        String stepUpToken,
        @Schema(description = "Lifetime of stepUpToken", example = "600")
        long expiresInSeconds
) {}
