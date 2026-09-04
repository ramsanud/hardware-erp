package com.hardware.erp.billing.dto;

import com.hardware.erp.billing.entity.PaymentSource;
import com.hardware.erp.billing.entity.SubscriptionPaymentStatus;
import com.hardware.erp.tenant.entity.SubscriptionTier;

import java.time.LocalDateTime;

public record SubscriptionPaymentResponse(
        Long paymentId,
        Long orderId,
        SubscriptionTier requestedTier,
        long amountPaise,
        String currency,
        SubscriptionPaymentStatus status,
        PaymentSource source,
        LocalDateTime capturedAt
) {}
