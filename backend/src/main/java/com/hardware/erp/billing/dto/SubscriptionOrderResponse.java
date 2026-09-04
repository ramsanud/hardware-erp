package com.hardware.erp.billing.dto;

import com.hardware.erp.billing.entity.SubscriptionOrderStatus;
import com.hardware.erp.tenant.entity.SubscriptionTier;

/** razorpayKeyId is the PUBLIC key - safe to send to the browser, it's what Razorpay Checkout.js needs to open the widget. */
public record SubscriptionOrderResponse(
        Long orderId,
        String razorpayOrderId,
        String razorpayKeyId,
        SubscriptionTier requestedTier,
        long amountPaise,
        String currency,
        SubscriptionOrderStatus status
) {}
