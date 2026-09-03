package com.hardware.erp.platformadmin.dto;

import java.time.LocalDateTime;

/**
 * Never carries the actual secrets back to the browser once saved - only
 * whether each is set. "source" tells the admin which credentials are
 * actually in force right now (see RazorpayConfigResolver): the database
 * row they can edit here, the RAZORPAY_* environment variables set at
 * deploy time, or neither.
 */
public record RazorpayConfigResponse(
        boolean enabled,
        String keyId,
        boolean keySecretConfigured,
        boolean webhookSecretConfigured,
        long proPlanAmountPaise,
        long maxPlanAmountPaise,
        String source,
        LocalDateTime updatedAt
) {}
