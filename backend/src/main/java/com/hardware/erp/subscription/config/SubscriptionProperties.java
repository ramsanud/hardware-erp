package com.hardware.erp.subscription.config;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-088 §14. Trial policy for newly registered shops. trialDays = 0 turns
 * trials off (new shop lands on BASIC, ACTIVE). The values live here, in
 * application.yml and the environment, and nowhere else.
 */
@ConfigurationProperties(prefix = "app.subscription")
public record SubscriptionProperties(
        Integer trialDays,
        SubscriptionTier trialTier,
        Integer pastDueGraceDays
) {
    public SubscriptionProperties {
        if (trialDays == null || trialDays < 0) {
            trialDays = 14;
        }
        if (trialTier == null) {
            trialTier = SubscriptionTier.MAX;
        }
        if (pastDueGraceDays == null || pastDueGraceDays < 0) {
            pastDueGraceDays = 7;
        }
    }

    public boolean trialEnabled() {
        return trialDays > 0;
    }
}
