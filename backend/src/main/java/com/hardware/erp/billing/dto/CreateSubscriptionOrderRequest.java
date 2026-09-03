package com.hardware.erp.billing.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import jakarta.validation.constraints.NotNull;

public record CreateSubscriptionOrderRequest(
        @NotNull SubscriptionTier requestedTier
) {}
