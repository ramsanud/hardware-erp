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
        String email,

        /**
         * CR-078. Required only when `email` differs from the current address
         * and that address has been verified: the login email is where
         * password resets and sign-in codes go, so changing it is a takeover
         * step and the present owner must re-confirm first. Obtained from
         * /step-up/send + /step-up/verify. Ignored when the email is not
         * changing, and not demanded when there is no verified address to
         * send a code to.
         */
        @Schema(description = "From /v1/auth/step-up/verify. Required when changing a verified email.")
        String stepUpToken
) {}
