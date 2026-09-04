package com.hardware.erp.platformadmin.dto;

import java.time.LocalDateTime;

public record PlatformAdminActiveSessionResponse(
        Long id,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        /** The most-recently-used active session for this admin - the closest honest proxy for "this session" without threading the caller's own refresh-token hash through every authenticated request. */
        boolean current
) {}
