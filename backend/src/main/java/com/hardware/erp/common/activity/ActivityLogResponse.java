package com.hardware.erp.common.activity;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One business change, as the shop reads it back (CR-072).
 *
 * The before/after maps are the reason this table is worth keeping, so they
 * are carried through rather than summarised - they are already redacted at
 * write time by ActivityLogServiceImpl.REDACTED, which strips passwords,
 * tokens and bank account numbers, so nothing sensitive reaches this DTO to
 * begin with.
 *
 * tenantId is deliberately NOT exposed. The caller's own tenant is the only
 * one they can ever read, so echoing it back tells them nothing and would put
 * an internal identifier on the wire for no reason.
 */
@Schema(name = "ActivityLogResponse")
public record ActivityLogResponse(

        @Schema(example = "8214") Long id,
        @Schema(example = "PRODUCT") String moduleCode,
        @Schema(example = "Product") String entityType,
        @Schema(example = "142") Long entityId,
        @Schema(description = "The record's name AS IT WAS at the time - it may since have been renamed.",
                example = "CPVC Elbow 25mm") String entityLabel,
        @Schema(example = "UPDATE") ActivityAction action,
        @Schema(description = "Only the fields that changed, not the whole row.") Map<String, Object> oldValues,
        @Schema(description = "Only the fields that changed, not the whole row.") Map<String, Object> newValues,
        @Schema(example = "5") Long userId,
        @Schema(description = "Snapshotted at the time. \"SYSTEM\" for a scheduled job.",
                example = "Karthik Raja") String fullName,
        @Schema(example = "MANAGER") String roleCode,
        @Schema(example = "192.168.1.22") String ipAddress,
        @Schema(example = "8f2a1c3d-1111-4a2b-9c3d-000000000003") String requestId,
        @Schema(example = "Cancelled - customer returned the goods") String remarks,
        @Schema(example = "2026-09-09T09:14:22.331") LocalDateTime createdAt
) {}
