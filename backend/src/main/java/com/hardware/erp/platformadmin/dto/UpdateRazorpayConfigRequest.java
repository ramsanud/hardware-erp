package com.hardware.erp.platformadmin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * keySecret/webhookSecret are null-means-"leave unchanged" - the frontend
 * form never receives the current secret back (RazorpayConfigResponse only
 * says whether one is set), so it cannot round-trip a value it never saw.
 * Sending a blank string, not null, is how a secret is deliberately cleared.
 */
public record UpdateRazorpayConfigRequest(
        @NotNull Boolean enabled,
        String keyId,
        String keySecret,
        String webhookSecret,
        @Positive Long proPlanAmountPaise,
        @Positive Long maxPlanAmountPaise
) {}
