package com.hardware.erp.platformadmin.dto;

/**
 * What every successful password check returns - never a full session.
 * enrollmentRequired tells the frontend whether to route to the QR-enrollment
 * screen or the "enter your 6-digit code" screen.
 */
public record PlatformAdminLoginChallengeResponse(
        String mfaToken,
        boolean enrollmentRequired,
        long expiresInSeconds
) {}
