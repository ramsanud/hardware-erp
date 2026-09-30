package com.hardware.erp.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FeatureResponse")
public record FeatureResponse(
        String featureKey,
        String featureName,
        String description,
        String moduleCode,
        int displayOrder,
        @Schema(description = "Cheapest plan that carries the feature") String minimumPlanCode,
        String minimumPlanName
) {}
