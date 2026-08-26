package com.hardware.erp.developer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Answers "may I inspect, and why not". Safe for any signed-in user to read:
 * it discloses which environment they are on and whether they personally hold
 * the permission, and nothing about the machine.
 */
@Schema(description = "Whether developer inspection is available to the calling user")
public record DeveloperInspectionStatusResponse(

        @Schema(description = "True only when the environment permits it AND the caller holds DEVELOPER_INSPECT",
                example = "false")
        boolean available,

        @Schema(description = "Environment half of the gate", example = "false")
        boolean environmentAllows,

        @Schema(description = "Person half of the gate: does the caller hold DEVELOPER_INSPECT", example = "false")
        boolean permissionHeld,

        @Schema(description = "Active Spring profiles. Names only - never any configured value.",
                example = "[\"prod\"]")
        List<String> activeProfiles
) {
}
