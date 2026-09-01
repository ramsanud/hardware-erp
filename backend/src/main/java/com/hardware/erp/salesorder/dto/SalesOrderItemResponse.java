package com.hardware.erp.salesorder.dto;

import com.hardware.erp.common.util.LineDiscount;

import java.math.BigDecimal;

public record SalesOrderItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal quantity,
        String unitPriceDisplay,
        String gstRatePercent,
        String lineSubtotalDisplay,
        String lineGstDisplay,
        String lineTotalDisplay,
        LineDiscount.Type discountType,
        String discountPercent,
        String discountDisplay,
        String lineGrossDisplay
) {}
