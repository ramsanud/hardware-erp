package com.hardware.erp.auth.dto;

import java.time.LocalDateTime;

/** CR-053 backlog item 6. One row per business change this user made, newest first. */
public record UserActivityResponse(
        Long id,
        String moduleCode,
        String entityType,
        Long entityId,
        String entityLabel,
        String action,
        String remarks,
        LocalDateTime createdAt
) {}
