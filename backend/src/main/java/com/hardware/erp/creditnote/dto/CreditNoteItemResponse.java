package com.hardware.erp.creditnote.dto;

import java.math.BigDecimal;

public record CreditNoteItemResponse(
        Long id,
        Long invoiceItemId,
        Long productId,
        String productName,
        BigDecimal quantity,
        String unitPriceDisplay,
        String gstRatePercent,
        String lineSubtotalDisplay,
        String lineGstDisplay,
        String lineTotalDisplay
) {}
