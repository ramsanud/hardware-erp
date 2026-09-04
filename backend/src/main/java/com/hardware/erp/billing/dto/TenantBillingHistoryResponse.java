package com.hardware.erp.billing.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;

import java.util.List;

public record TenantBillingHistoryResponse(
        SubscriptionTier currentTier,
        List<SubscriptionPaymentResponse> payments
) {}
