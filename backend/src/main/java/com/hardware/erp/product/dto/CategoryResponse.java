package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CategoryResponse")
public record CategoryResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "CAT-0003") String categoryCode,
        @Schema(example = "Hand Tools") String categoryName,
        @Schema(example = "1") Long parentCategoryId,
        @Schema(example = "Tools") String parentCategoryName,
        @Schema(example = "Hammers, wrenches and similar tools") String description,
        @Schema(example = "ACTIVE") CategoryStatus status,
        @Schema(description = "Number of products directly in this category", example = "12")
        long productCount
) {}
