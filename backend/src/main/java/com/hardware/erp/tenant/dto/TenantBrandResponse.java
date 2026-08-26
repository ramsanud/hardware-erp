package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;

/**
 * Just enough to render the sidebar brand (CR-023) - no GST/address detail,
 * so every authenticated user can read it, not only SETTINGS_VIEW holders.
 * subscriptionTier rides along too (CR-027): feature gates like the AI
 * assistant need to be visible to any staff member, not only the owner.
 */
public record TenantBrandResponse(String name, boolean hasLogo, SubscriptionTier subscriptionTier) {}
