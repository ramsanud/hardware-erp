package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * An active session. Deliberately exposes no token material - not the raw
 * token, not the hash - only enough for a user to recognise a device they do
 * not own and revoke it.
 */
@Schema(name = "SessionResponse")
public record SessionResponse(

        @Schema(example = "17") Long id,
        @Schema(example = "192.168.1.24") String ipAddress,
        @Schema(example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") String userAgent,
        @Schema(example = "2026-08-13T09:14:22.331") LocalDateTime createdAt,
        @Schema(example = "2026-08-13T11:02:10.884") LocalDateTime lastUsedAt,
        @Schema(example = "2026-08-20T09:14:22.331") LocalDateTime expiresAt,
        @Schema(description = "True for the session making this request", example = "true")
        boolean current
) {}
