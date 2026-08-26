package com.hardware.erp.auth.dto;

import com.hardware.erp.auth.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "UpdateUserRequest")
public record UpdateUserRequest(

        @Schema(example = "Karthik Raja S")
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

        @Schema(example = "3")
        @NotNull(message = "Role is required")
        @Positive(message = "Role is required")
        Long roleId,

        @Schema(example = "ACTIVE")
        @NotNull(message = "Status is required")
        UserStatus status
) {}
