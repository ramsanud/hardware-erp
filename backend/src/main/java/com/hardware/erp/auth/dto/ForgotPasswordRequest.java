package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ForgotPasswordRequest")
public record ForgotPasswordRequest(

        @Schema(description = "Mobile number or email", example = "9843012345")
        @NotBlank(message = "Mobile number or email is required")
        @Size(max = 255)
        String identifier
) {}
