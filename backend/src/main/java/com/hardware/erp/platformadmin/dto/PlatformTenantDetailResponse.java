package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Tenant Detail page. Deliberately aggregate counts only - never a list of
 * the tenant's own customers/invoices/etc. A platform admin's job is to
 * operate the platform, not browse a shop's business data (section 55 of
 * the Platform Admin spec: "use the minimum data necessary").
 */
@Schema(name = "PlatformTenantDetailResponse")
public record PlatformTenantDetailResponse(
        Long id,
        String name,
        String slug,
        String ownerName,
        String ownerEmail,
        String phone,
        String email,
        String city,
        String stateCode,
        SubscriptionTier subscriptionTier,
        /** Null unless the current tier came from a redeemed trial coupon (CR-032) - see Tenant.subscriptionTrialExpiresAt. */
        LocalDateTime subscriptionTrialExpiresAt,
        TenantStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastActiveAt,
        Usage usage,
        /** Null when this tenant has never connected WhatsApp - see notification.entity.TenantWhatsAppConnection (CR-056). */
        String whatsAppConnectionStatus
) {
    public record Usage(
            long users,
            long customers,
            long products,
            long invoices,
            long purchases,
            long payments,
            long expenses
    ) {}
}
