package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionCouponStatus;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record SubscriptionCouponRequest(
        @NotBlank @Size(max = 30) String code,
        @Size(max = 255) String description,
        @NotNull SubscriptionTier grantedTier,
        @NotNull @Positive Integer trialDays,
        LocalDate validFrom,
        LocalDate validUntil,
        @Positive Integer usageLimit,
        @NotNull SubscriptionCouponStatus status
) {}
