package com.hardware.erp.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductImportConfirmRow(
        int rowNumber,
        @NotBlank @Size(max = 255) String productName,
        @Size(max = 30) String productCode,
        Long categoryId,
        Long brandId,
        @NotBlank @Size(max = 20) String unit,
        @Size(max = 10) String hsnCode,
        @NotNull BigDecimal gstRatePercent,
        @NotNull BigDecimal purchasePriceRupees,
        @NotNull BigDecimal sellingPriceRupees,
        @NotNull BigDecimal mrpRupees,
        BigDecimal minimumStock,
        BigDecimal reorderLevel
) {}
