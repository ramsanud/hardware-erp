package com.hardware.erp.purchase.dto;

import java.math.BigDecimal;
import java.util.List;

public record ImportRowPreview(
        int rowNumber,
        String productName,
        String brandName,
        String categoryName,
        String sku,
        BigDecimal quantity,
        String unit,
        Long unitPricePaise,
        BigDecimal gstRatePercent,
        Long lineTotalPaise,
        boolean productIsExisting,
        Long matchedProductId,
        String matchedProductName,
        BigDecimal matchedProductCurrentStock,
        Long matchedProductCurrentPurchasePricePaise,
        boolean brandIsExisting,
        Long matchedBrandId,
        boolean categoryIsExisting,
        Long matchedCategoryId,
        List<String> errors
) {}
