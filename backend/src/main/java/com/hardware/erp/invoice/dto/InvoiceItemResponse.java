package com.hardware.erp.invoice.dto;

import java.math.BigDecimal;

public record InvoiceItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal quantity,
        String unit,
        String unitPriceDisplay,
        String gstRatePercent,
        String lineSubtotalDisplay,
        String lineGstDisplay,
        String lineTotalDisplay
) {}
