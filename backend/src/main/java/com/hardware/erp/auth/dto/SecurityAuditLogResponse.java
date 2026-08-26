package com.hardware.erp.auth.dto;

import com.hardware.erp.auth.entity.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "SecurityAuditLogResponse")
public record SecurityAuditLogResponse(

        @Schema(example = "1042") Long id,
        @Schema(example = "LOGIN_FAILURE") AuditAction action,
        @Schema(example = "USER") String entityType,
        @Schema(example = "5") Long entityId,
        @Schema(example = "5") Long userId,
        @Schema(example = "Karthik Raja") String fullName,
        @Schema(example = "false") boolean success,
        @Schema(example = "Wrong password, attempt 2") String failureReason,
        @Schema(example = "192.168.1.22") String ipAddress,
        @Schema(example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") String userAgent,
        @Schema(example = "8f2a1c3d-1111-4a2b-9c3d-000000000003") String requestId,
        @Schema(example = "2026-08-13T09:14:22.331") LocalDateTime createdAt
) {}
