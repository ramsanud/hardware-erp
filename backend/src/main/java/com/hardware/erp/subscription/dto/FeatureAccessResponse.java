package com.hardware.erp.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FeatureAccessResponse")
public record FeatureAccessResponse(
        String featureKey,
        String featureName,
        boolean allowed,
        String currentPlanCode,
        String currentPlanName,
        @Schema(description = "Null when allowed") String requiredPlanCode,
        String requiredPlanName
) {}
