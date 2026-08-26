package com.hardware.erp.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductImportRowPreview(
        int rowNumber,
        String productName,
        String productCode,
        String categoryName,
        Long matchedCategoryId,
        String brandName,
        Long matchedBrandId,
        String unit,
        String hsnCode,
        BigDecimal gstRatePercent,
        BigDecimal purchasePriceRupees,
        BigDecimal sellingPriceRupees,
        BigDecimal mrpRupees,
        BigDecimal minimumStock,
        BigDecimal reorderLevel,
        List<String> errors
) {}
