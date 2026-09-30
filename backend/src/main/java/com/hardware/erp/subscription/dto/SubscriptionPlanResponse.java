package com.hardware.erp.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SubscriptionPlanResponse")
public record SubscriptionPlanResponse(
        String planCode,
        @Schema(description = "The locked tenant.subscription_tier value the plan maps to") String tier,
        String planName,
        String tagline,
        long pricePaise,
        @Schema(example = "₹599") String priceDisplay,
        String currency,
        String billingPeriod,
        boolean recommended,
        int displayOrder,
        List<String> featureKeys,
        List<UsageLimitResponse> usageLimits
) {}
