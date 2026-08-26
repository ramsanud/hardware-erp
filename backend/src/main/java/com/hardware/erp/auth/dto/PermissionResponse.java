package com.hardware.erp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PermissionResponse")
public record PermissionResponse(

        @Schema(example = "12") Long id,
        @Schema(example = "PRODUCT_VIEW_COST") String code,
        @Schema(example = "View product cost") String name,
        @Schema(example = "See purchase cost and margin") String description,
        @Schema(example = "PRODUCT") String moduleCode,
        @Schema(example = "30") Integer displayOrder
) {}
