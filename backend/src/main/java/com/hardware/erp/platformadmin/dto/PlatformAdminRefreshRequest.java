package com.hardware.erp.platformadmin.dto;

/** refreshToken is null whenever the transport is the HttpOnly cookie - see RefreshTokenCookieService's tenant-side equivalent. */
public record PlatformAdminRefreshRequest(
        String refreshToken
) {}
