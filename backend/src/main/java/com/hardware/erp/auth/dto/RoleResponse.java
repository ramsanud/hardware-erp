package com.hardware.erp.auth.dto;

import com.hardware.erp.auth.entity.RoleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(name = "RoleResponse")
public record RoleResponse(

        @Schema(example = "4") Long id,
        @Schema(example = "STAFF") String code,
        @Schema(example = "Staff") String name,
        @Schema(example = "Billing counter, no cost visibility") String description,
        @Schema(description = "System roles cannot be deleted or renamed", example = "true")
        boolean systemRole,
        @Schema(example = "ACTIVE") RoleStatus status,
        Set<String> permissions,
        @Schema(description = "How many users hold this role", example = "3") long userCount
) {}
