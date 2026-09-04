package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;

/**
 * Just enough to render the sidebar brand (CR-023) - no GST/address detail,
 * so every authenticated user can read it, not only SETTINGS_VIEW holders.
 * subscriptionTier rides along too (CR-027): feature gates like the AI
 * assistant need to be visible to any staff member, not only the owner.
 */
public record TenantBrandResponse(
        String name, boolean hasLogo, SubscriptionTier subscriptionTier,
        /**
         * CR-053 backlog item 1. Same reasoning as subscriptionTier above -
         * whether the Price History section renders on a product page, and
         * whether the Free Qty field appears on invoice line entry, needs to
         * be visible to any staff member (a counter operator with only
         * PRODUCT_VIEW/INVOICE_CREATE), not only SETTINGS_VIEW holders.
         */
        boolean showPriceHistory,
        boolean enableFreeQuantity,
        /** CR-053 backlog item 3. Informational-only TDS/TCS, same "every staff member needs to see this" reasoning. */
        boolean tdsEnabled,
        java.math.BigDecimal tdsRatePercent,
        boolean tcsEnabled,
        java.math.BigDecimal tcsRatePercent,
        /** CR-053 backlog item 4. Shows the e-Invoice review section on Invoice detail - visible to any staff member who can view an invoice. */
        boolean einvoiceEnabled
) {}
