package com.hardware.erp.tenant.dto;

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
        LocalDateTime subscriptionTrialExpiresAt
) {}
