package com.hardware.erp.product.dto;

import com.hardware.erp.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "ProductResponse")
public record ProductResponse(

        @Schema(example = "42") Long id,
        @Schema(example = "PRD-000042") String productCode,
        @Schema(example = "Godrej Ultra Premium Lock 60mm") String productName,

        @Schema(example = "3") Long categoryId,
        @Schema(example = "Hand Tools") String categoryName,
        @Schema(example = "4") Long brandId,
        @Schema(example = "Godrej") String brandName,

        @Schema(example = "UL-60-BR") String modelNo,
        @Schema(example = "GDJ-LK-6001") String manufacturerCode,
        @Schema(example = "8901030812345") String barcode,
        @Schema(example = "PCS") String unit,
        @Schema(example = "5-lever brass lock body, brown finish") String description,
        @Schema(example = "8301") String hsnCode,
        @Schema(example = "18.00") BigDecimal gstRatePercent,

        @Schema(description = "Null unless the caller holds PRODUCT_VIEW_COST", example = "450.00")
        Long purchasePricePaise,
        @Schema(description = "Null unless the caller holds PRODUCT_VIEW_COST", example = "450.00")
        String purchasePriceDisplay,

        @Schema(example = "650.00") Long sellingPricePaise,
        @Schema(example = "650.00") String sellingPriceDisplay,
        @Schema(example = "750.00") Long mrpPaise,
        @Schema(example = "750.00") String mrpDisplay,

        @Schema(example = "5") BigDecimal minimumStock,
        @Schema(example = "10") BigDecimal reorderLevel,

        @Schema(example = "ACTIVE") ProductStatus status,

        @Schema(example = "2026-08-22T09:14:22.331") LocalDateTime createdAt,
        @Schema(example = "2026-08-22T09:14:22.331") LocalDateTime updatedAt,
        boolean hasImage,

        @Schema(description = "CR-053 backlog item 1", example = "BOX") String altUnitLabel,
        @Schema(example = "12") BigDecimal altUnitConversionFactor
) {}
