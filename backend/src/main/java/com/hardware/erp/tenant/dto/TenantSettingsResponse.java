package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.InvoiceTheme;
import com.hardware.erp.tenant.entity.SubscriptionTier;

import java.time.LocalDateTime;

public record TenantSettingsResponse(
        Long id,
        String name,
        String gstNo,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String pincode,
        String signatoryName,
        boolean hasLogo,
        boolean hasSignatureImage,
        boolean hasUpiQrImage,
        String panNo,
        String phone,
        String email,
        String bankAccountName,
        String bankAccountNo,
        String bankIfsc,
        String bankName,
        String upiId,
        SubscriptionTier subscriptionTier,
        /** CR-032 - null means subscriptionTier (whatever it is) is permanent, not from a trial coupon. */
        LocalDateTime subscriptionTrialExpiresAt,
        /** CR-053. Shop-wide default skin for the generated invoice PDF. */
        InvoiceTheme invoiceTheme,

        /** CR-053 backlog item 1 - see TenantSettingsRequest. */
        boolean showItemDescription,
        boolean showAlternateUnit,
        boolean showPriceHistory,
        boolean enableFreeQuantity,
        boolean showInvoiceTime,
        boolean showItemImage,
        String invoiceTagline,

        /** CR-053 backlog item 3. Informational only - see V41's migration comment. */
        boolean tdsEnabled,
        String tdsSectionCode,
        java.math.BigDecimal tdsRatePercent,
        boolean tcsEnabled,
        String tcsSectionCode,
        java.math.BigDecimal tcsRatePercent,

        /** CR-053 backlog item 4. */
        boolean einvoiceEnabled,

        /** CR-053 backlog item 5. */
        boolean paymentDueReminderEnabled,
        boolean lowStockAlertEnabled
) {}
