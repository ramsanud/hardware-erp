package com.hardware.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for /mfa/enroll, which needs only the challenge token from login. */
public record MfaTokenRequest(
        @NotBlank String mfaToken
) {}
