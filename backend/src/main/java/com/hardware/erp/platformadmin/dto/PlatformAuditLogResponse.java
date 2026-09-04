package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.PlatformAuditAction;

import java.time.LocalDateTime;

public record PlatformAuditLogResponse(
        Long id,
        /** Null for a system-triggered event (e.g. an auto-resolved incident) - see PlatformAuditService's own null-admin support. */
        Long adminId,
        /** Null when adminId is null, or when that admin account no longer exists - the log itself must remain readable either way. */
        String adminEmail,
        PlatformAuditAction action,
        boolean success,
        String targetType,
        Long targetId,
        String detail,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {}
