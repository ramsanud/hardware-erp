package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Grouped by module so the role screen renders a section per module. */
@Schema(name = "PermissionGroupResponse")
public record PermissionGroupResponse(

        @Schema(example = "PRODUCT") String moduleCode,
        List<PermissionResponse> permissions
) {}
