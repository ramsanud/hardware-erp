package com.hardware.erp.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CR-078. The address the signup wizard is about to register, which must first prove it can receive mail. */
@Schema(name = "RegistrationCodeRequest")
public record RegistrationCodeRequest(
        @Schema(example = "owner@newshop.in")
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255)
        String email
) {}
