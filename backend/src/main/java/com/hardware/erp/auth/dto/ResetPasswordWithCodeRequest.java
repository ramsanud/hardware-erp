package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CR-078 - the code path of forgot-password. The same email that carries the
 * reset link carries a six-digit code, for a phone where tapping the link
 * opens the wrong browser or none at all.
 */
@Schema(name = "ResetPasswordWithCodeRequest")
public record ResetPasswordWithCodeRequest(
        @Schema(description = "The mobile number or email the forgot-password request was made with",
                example = "9843012345")
        @NotBlank(message = "Mobile number or email is required")
        @Size(max = 255)
        String identifier,

        @Schema(description = "The six-digit code from the email", example = "482913")
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code")
        String code,

        @Schema(example = "NewPass@2026")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        String newPassword
) {}
