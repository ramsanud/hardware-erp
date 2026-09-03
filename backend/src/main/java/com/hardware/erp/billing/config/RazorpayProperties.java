package com.hardware.erp.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials. Disabled by default and treated as inactive whenever
 * key-id/key-secret are blank - same "unconfigured means honest 'not
 * configured' error, never a fake success" convention as CaptchaProperties
 * and WhatsAppProperties. webhookSecret is separate from keySecret because
 * Razorpay issues them independently (Dashboard -> Settings -> Webhooks).
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(
        boolean enabled,
        String keyId,
        String keySecret,
        String webhookSecret,
        String apiBaseUrl,
        /** Monthly price to move a tenant to PRO, in paise. Placeholder pricing - an operator-configurable value, not a hardcoded business decision. */
        Long proPlanAmountPaise,
        Long maxPlanAmountPaise
) {
    public RazorpayProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://api.razorpay.com/v1";
        }
        if (proPlanAmountPaise == null) {
            proPlanAmountPaise = 99_900L; // Rs 999/month placeholder
        }
        if (maxPlanAmountPaise == null) {
            maxPlanAmountPaise = 299_900L; // Rs 2,999/month placeholder
        }
    }

    /** Enabled AND both order-creation credentials present. */
    public boolean active() {
        return enabled && keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    /** Webhook signature verification needs only the webhook secret - independent of order-creation being active. */
    public boolean webhookActive() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
