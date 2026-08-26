package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The list-screen projection. Omits purchase price unconditionally - a list
 * of 100 products should not carry cost data past the network even for a
 * PRODUCT_VIEW_COST holder; the detail screen is where cost is read.
 */
@Schema(name = "ProductSummaryResponse")
public record ProductSummaryResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "PRD-000042") String productCode,
        @Schema(example = "Godrej Ultra Premium Lock 60mm") String productName,
        @Schema(example = "Hand Tools") String categoryName,
        @Schema(example = "Godrej") String brandName,
        @Schema(example = "PCS") String unit,
        @Schema(example = "650.00") String sellingPriceDisplay,
        @Schema(example = "18.00") String gstRatePercent,
        @Schema(example = "ACTIVE") ProductStatus status,
        boolean hasImage
) {}
