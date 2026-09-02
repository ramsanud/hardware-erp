package com.hardware.erp.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** CR-053 backlog item 1. One row per invoice line this product has appeared on, most recent first. */
@Schema(name = "ProductPriceHistoryResponse")
public record ProductPriceHistoryResponse(
        @Schema(example = "2026-08-20") LocalDate invoiceDate,
        @Schema(example = "INV-000042") String invoiceNumber,
        @Schema(example = "Suresh & Co.") String customerName,
        @Schema(example = "5") BigDecimal quantity,
        @Schema(example = "650.00") String unitPriceDisplay
) {}
