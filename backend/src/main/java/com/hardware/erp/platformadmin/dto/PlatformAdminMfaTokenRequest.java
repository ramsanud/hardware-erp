package com.hardware.erp.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for /mfa/enroll, which needs only the challenge token from login. */
public record PlatformAdminMfaTokenRequest(
        @NotBlank String mfaToken
) {}
