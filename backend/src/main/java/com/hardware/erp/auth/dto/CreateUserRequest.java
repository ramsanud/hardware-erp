package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * There is no public self-registration (CR-008). This endpoint is the only way
 * an account comes into existence, and it requires USER_MANAGE.
 */
@Schema(name = "CreateUserRequest", description = "Owner creates an employee account")
public record CreateUserRequest(

        @Schema(example = "Karthik Raja")
        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Full name must be 200 characters or fewer")
        String fullName,

        @Schema(example = "9843012345")
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$",
                 message = "Enter a valid 10-digit Indian mobile number")
        String mobileNo,

        @Schema(example = "karthik@sarahardware.in")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email is too long")
        String email,

        @Schema(example = "EMP005")
        @Size(max = 30, message = "Employee code must be 30 characters or fewer")
        String employeeCode,

        @Schema(example = "4")
        @NotNull(message = "Role is required")
        @Positive(message = "Role is required")
        Long roleId,

        @Schema(description = "Temporary password. The user is forced to change it.",
                example = "Welcome@2026")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        String password,

        @Schema(description = "Force a password change at first sign-in", example = "true")
        boolean mustChangePassword
) {}
