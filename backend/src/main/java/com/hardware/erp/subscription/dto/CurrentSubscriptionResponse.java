package com.hardware.erp.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "CurrentSubscriptionResponse")
public record CurrentSubscriptionResponse(
        String planCode,
        String planName,
        String tier,
        String status,
        @Schema(description = "Plan whose features apply right now - BASIC when the subscription is expired, cancelled or suspended")
        String effectivePlanCode,
        String effectivePlanName,
        LocalDateTime trialEndsAt,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        LocalDateTime renewalAt,
        LocalDateTime cancelledAt,
        String paymentStatus,
        @Schema(description = "True when a payment gateway is configured, so upgrades go through checkout") boolean checkoutRequiredForUpgrade,
        List<String> featureKeys,
        UsageResponse usage,
        List<SubscriptionHistoryResponse> history
) {}
