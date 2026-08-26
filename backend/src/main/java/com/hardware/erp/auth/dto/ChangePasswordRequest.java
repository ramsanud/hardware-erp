package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(

        @Schema(example = "Welcome@2026")
        @NotBlank(message = "Current password is required")
        @Size(max = 128)
        String currentPassword,

        @Schema(example = "Counter@2026")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        String newPassword
) {}
