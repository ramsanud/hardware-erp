package com.hardware.erp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "StockResponse")
public record StockResponse(
        @Schema(example = "42") Long productId,
        @Schema(example = "PRD-000042") String productCode,
        @Schema(example = "Godrej Ultra Premium Lock 60mm") String productName,
        @Schema(example = "PCS") String unit,
        @Schema(example = "24.0000") BigDecimal quantityOnHand,
        @Schema(example = "5.0000") BigDecimal reorderLevel,
        @Schema(example = "false") boolean lowStock
) {}
