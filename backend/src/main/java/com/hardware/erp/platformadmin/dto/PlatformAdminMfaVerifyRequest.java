package com.hardware.erp.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for both /mfa/verify (login) and /mfa/enroll/confirm - same shape, different meaning of "code". */
public record PlatformAdminMfaVerifyRequest(
        @NotBlank String mfaToken,
        @NotBlank String code
) {}
