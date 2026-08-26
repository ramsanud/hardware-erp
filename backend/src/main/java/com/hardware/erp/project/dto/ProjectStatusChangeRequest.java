package com.hardware.erp.project.dto;

import com.hardware.erp.project.entity.ProjectOutcome;
import com.hardware.erp.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ProjectStatusChangeRequest",
        description = "outcome is required when (and only meaningful when) status is COMPLETED")
public record ProjectStatusChangeRequest(
        @NotNull(message = "Status is required")
        ProjectStatus status,

        @Schema(description = "SUCCESS or FAILURE - required only when status is COMPLETED")
        ProjectOutcome outcome
) {}
