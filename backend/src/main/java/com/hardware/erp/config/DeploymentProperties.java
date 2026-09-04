package com.hardware.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-059 - the deployment switch, bound from {@code app.deployment}.
 *
 * Set it with the APP_DEPLOYMENT_MODE environment variable, or let the
 * 'cloud' / 'selfhosted' profile set it (application-cloud.yml and
 * application-selfhosted.yml each pin their own, which is why those two
 * profiles are mutually exclusive - DeploymentModeGuard rejects both at once).
 *
 * Defaults to CLOUD so that every pre-CR-059 configuration - dev, local, test,
 * and the existing Render deployment - keeps behaving exactly as it did.
 * Self-hosting is opt-in; nothing silently changes because this class was
 * added.
 */
@ConfigurationProperties(prefix = "app.deployment")
public record DeploymentProperties(
        DeploymentMode mode,

        /**
         * Shown in the UI footer and in the startup banner so a support call
         * can establish which installation is being described without asking
         * the client to read a config file. Free text; blank is fine.
         */
        String installationName,

        /**
         * Escape hatch for the one genuinely awkward case: a self-hosted
         * install that the operator DOES want to bill through Razorpay (a
         * reseller running instances for several shops on their own hardware).
         * Off by default - a client who bought a self-hosted licence must
         * never be shown an upgrade prompt for software they already own.
         */
        Boolean billingEnabled
) {
    public DeploymentProperties {
        if (mode == null) {
            mode = DeploymentMode.CLOUD;
        }
        if (installationName == null) {
            installationName = "";
        }
        // Cloud bills by default; self-hosted does not. Explicit true/false
        // from configuration always wins over both.
        if (billingEnabled == null) {
            billingEnabled = mode.isCloud();
        }
    }

    public boolean selfHosted() {
        return mode.isSelfHosted();
    }

    /** True when subscription checkout, plan caps and upgrade prompts apply. */
    public boolean billingApplies() {
        return Boolean.TRUE.equals(billingEnabled);
    }
}
