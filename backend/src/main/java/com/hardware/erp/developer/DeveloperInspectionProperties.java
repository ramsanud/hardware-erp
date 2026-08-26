package com.hardware.erp.developer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The ENVIRONMENT half of the developer-inspection gate (CR-045).
 *
 * Deliberately separate from any role or permission. A shop owner is an
 * administrator, not a developer: administering a business and debugging the
 * software that runs it are different jobs, and conflating them is how
 * production ends up with a diagnostics console one stolen owner password
 * away. Access therefore requires BOTH this flag and the DEVELOPER_INSPECT
 * permission, which no default role holds.
 *
 * Bound from app.developer-inspection.*. False in application.yml, so any
 * profile that does not deliberately opt in has it off, and a hard false
 * in application-prod.yml with no ${...} placeholder behind it.
 */
@ConfigurationProperties(prefix = "app.developer-inspection")
public record DeveloperInspectionProperties(

        /**
         * Whether this environment permits developer diagnostics at all.
         * Ignored - forced off - whenever the 'prod' profile is active; see
         * DeveloperInspectionService.
         */
        boolean enabled
) {
}
