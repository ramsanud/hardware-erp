package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(

        @Schema(description = "Single-use token from the reset email",
                example = "K3n8Qm2pLxR7vT1wY5zA9bC4dE6fG0hJ")
        @NotBlank(message = "Reset token is required")
        @Size(max = 128)
        String token,

        @Schema(example = "NewPass@2026")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        String newPassword
) {}
