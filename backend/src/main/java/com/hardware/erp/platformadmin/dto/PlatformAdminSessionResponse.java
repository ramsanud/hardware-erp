package com.hardware.erp.platformadmin.dto;

public record PlatformAdminSessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        PlatformAdminResponse admin
) {}
