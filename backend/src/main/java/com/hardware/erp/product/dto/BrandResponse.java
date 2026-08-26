package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.BrandStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BrandResponse")
public record BrandResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "BRD-0004") String brandCode,
        @Schema(example = "Godrej") String brandName,
        @Schema(example = "Locks, furniture fittings and security hardware") String description,
        @Schema(example = "ACTIVE") BrandStatus status,
        @Schema(description = "Number of products under this brand", example = "8")
        long productCount
) {}
