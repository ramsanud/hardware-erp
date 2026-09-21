package com.hardware.erp.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePlanRequest")
public record ChangePlanRequest(
        @Schema(example = "PRO")
        @NotBlank(message = "Plan code is required")
        @Pattern(regexp = "^[A-Z_]{2,20}$", message = "Plan code must be uppercase letters")
        String planCode,
        @Size(max = 255) String reason
) {}
