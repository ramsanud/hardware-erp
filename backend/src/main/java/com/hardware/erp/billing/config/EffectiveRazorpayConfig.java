package com.hardware.erp.billing.config;

/**
 * The credentials/pricing actually in force right now - either the
 * platform-admin-configured database row, or the RAZORPAY_* environment
 * variables as a fallback. See RazorpayConfigResolver for the precedence
 * rule. apiBaseUrl is deliberately env-var only - not something a
 * "fill in your keys" settings page needs to expose.
 */
public record EffectiveRazorpayConfig(
        boolean active,
        String keyId,
        String keySecret,
        boolean webhookActive,
        String webhookSecret,
        String apiBaseUrl,
        long proPlanAmountPaise,
        long maxPlanAmountPaise
) {}
