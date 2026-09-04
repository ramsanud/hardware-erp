package com.hardware.erp.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * CR-058. The recycle-bin projection - see SupplierDeletedResponse. Carries no
 * price at all, cost or selling: this screen exists to identify and restore a
 * row, and PRODUCT_VIEW_COST would otherwise have to be re-litigated here.
 */
@Schema(name = "ProductDeletedResponse")
public record ProductDeletedResponse(

        @Schema(example = "42") Long id,
        @Schema(example = "PRD-000042") String productCode,
        @Schema(example = "Godrej Ultra Premium Lock 60mm") String productName,
        @Schema(example = "Hand Tools") String categoryName,
        @Schema(example = "Godrej") String brandName,
        @Schema(example = "2026-08-30T11:04:00.000") LocalDateTime deletedAt
) {}
