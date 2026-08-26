package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionCouponStatus;
import com.hardware.erp.tenant.entity.SubscriptionTier;

import java.time.LocalDate;

public record SubscriptionCouponResponse(
        Long id,
        String code,
        String description,
        SubscriptionTier grantedTier,
        int trialDays,
        LocalDate validFrom,
        LocalDate validUntil,
        Integer usageLimit,
        int timesUsed,
        SubscriptionCouponStatus status,
        boolean currentlyRedeemable
) {}
