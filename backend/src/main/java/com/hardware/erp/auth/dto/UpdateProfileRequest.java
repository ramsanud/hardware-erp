package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile edit. Carries no user id: the identity comes from the
 * security context, never from the request body.
 */
@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequest(

        @Schema(example = "Karthik Raja S")
        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Full name must be 200 characters or fewer")
        String fullName,

        @Schema(example = "karthik@sarahardware.in")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email is too long")
        String email
) {}
