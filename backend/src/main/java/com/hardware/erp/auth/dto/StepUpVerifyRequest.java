package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** CR-078. The code from /step-up/send, entered by a user who is already signed in. */
@Schema(name = "StepUpVerifyRequest")
public record StepUpVerifyRequest(
        @Schema(example = "482913")
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code")
        String code
) {}
