package com.hardware.erp.quotation.dto;

import java.math.BigDecimal;

public record QuotationItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal quantity,
        String unitPriceDisplay,
        String gstRatePercent,
        String lineSubtotalDisplay,
        String lineGstDisplay,
        String lineTotalDisplay
) {}
