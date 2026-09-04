package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.FeatureFlagScope;

import java.time.LocalDateTime;

public record FeatureFlagResponse(
        Long id,
        String flagKey,
        String name,
        String description,
        boolean enabled,
        FeatureFlagScope scope,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
