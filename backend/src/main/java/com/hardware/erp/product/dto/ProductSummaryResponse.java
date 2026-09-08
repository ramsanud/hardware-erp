package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The list-screen projection. Omits purchase price unconditionally - a list
 * of 100 products should not carry cost data past the network even for a
 * PRODUCT_VIEW_COST holder; the detail screen is where cost is read.
 *
 * CR-068 added the identification fields below the status. They are here
 * because the list's columns are now the user's choice: a shop that looks its
 * stock up by barcode, and one that looks it up by model number, want
 * different columns on the same screen, and the server cannot know which. All
 * five are already visible on the detail screen and on the printed pack, so
 * none of them widens what a PRODUCT_VIEW holder may read - unlike purchase
 * price, which stays out for exactly that reason.
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
        boolean hasImage,
        @Schema(example = "Hardened steel body, 5 levers, brass finish") String description,
        @Schema(example = "ULP-60") String modelNo,
        @Schema(example = "8901234567890") String barcode,
        @Schema(example = "8301") String hsnCode,
        @Schema(example = "780.00") String mrpDisplay
) {}
