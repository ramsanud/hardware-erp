package com.hardware.erp.platformadmin.dto;

/** secretBase32 is shown once as manual-entry fallback for an app that cannot scan the QR. */
public record PlatformAdminMfaEnrollResponse(
        String otpAuthUri,
        String qrCodePngBase64,
        String secretBase32
) {}
