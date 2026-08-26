package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;

import java.time.LocalDateTime;

public record SubscriptionCouponRedemptionResponse(
        SubscriptionTier grantedTier,
        LocalDateTime trialExpiresAt
) {}
