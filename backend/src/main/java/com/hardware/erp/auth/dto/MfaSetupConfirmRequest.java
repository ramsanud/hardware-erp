package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** CR-078. Confirms an authenticator app added from the profile by a user who is already signed in. */
@Schema(name = "MfaSetupConfirmRequest")
public record MfaSetupConfirmRequest(
        @Schema(example = "482913")
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code from the app")
        String code
) {}
