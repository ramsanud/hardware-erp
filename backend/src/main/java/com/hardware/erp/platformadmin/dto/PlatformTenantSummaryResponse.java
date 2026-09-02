package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** One row in the Platform Admin "Tenants" table. */
@Schema(name = "PlatformTenantSummaryResponse")
public record PlatformTenantSummaryResponse(
        Long id,
        String name,
        String slug,
        /** Null when the tenant has no ACTIVE owner right now (e.g. every owner was deactivated). */
        String ownerName,
        String ownerEmail,
        String phone,
        String email,
        SubscriptionTier subscriptionTier,
        TenantStatus status,
        LocalDateTime createdAt,
        /** Null when nobody at this tenant has ever signed in. */
        LocalDateTime lastActiveAt,
        long userCount
) {}
