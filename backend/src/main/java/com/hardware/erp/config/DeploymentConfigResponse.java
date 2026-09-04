package com.hardware.erp.config;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CR-059 - the public shape of {@link DeploymentProperties}. Deliberately a
 * separate record rather than serialising the properties class directly, so
 * that adding an operational setting there (a database host, a licence key)
 * cannot leak it to an unauthenticated caller by accident.
 */
@Schema(description = "Which installation this is, and what it supports")
public record DeploymentConfigResponse(

        @Schema(description = "CLOUD for the hosted SaaS, SELF_HOSTED for an on-premise Docker install", example = "SELF_HOSTED")
        DeploymentMode mode,

        @Schema(description = "Convenience flag - true when mode is SELF_HOSTED", example = "true")
        boolean selfHosted,

        @Schema(description = "Whether subscription checkout and plan-upgrade prompts apply here", example = "false")
        boolean billingEnabled,

        @Schema(description = "Operator-set label for this installation, shown in support contexts. May be blank.", example = "Sharma Hardware - Nagpur")
        String installationName
) {}
