package com.hardware.erp.quotation.dto;

import com.hardware.erp.common.util.LineDiscount;

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
        String lineTotalDisplay,
        /** CR-047 - mirrors InvoiceItemResponse so the conversion UI reads one shape. */
        LineDiscount.Type discountType,
        String discountPercent,
        String discountDisplay,
        String lineGrossDisplay
) {}
