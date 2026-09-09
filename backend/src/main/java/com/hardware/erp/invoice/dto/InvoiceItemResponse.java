package com.hardware.erp.invoice.dto;

import com.hardware.erp.common.util.LineDiscount;

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
        String lineTotalDisplay,
        /**
         * CR-047, narrowed by CR-050. NONE / PERCENTAGE - what the owner chose.
         * AMOUNT was retired in V33 and the constant no longer exists; this
         * comment outlived it and said otherwise.
         */
        LineDiscount.Type discountType,
        /** Only meaningful for PERCENTAGE; "0" otherwise. */
        String discountPercent,
        /** Authoritative money figure for both types, formatted like every other amount. */
        String discountDisplay,
        /** quantity x unit price, before the discount - what the PDF prints as the struck-through amount. */
        String lineGrossDisplay,
        /** CR-053 backlog item 1. Zero when the shop does not use free quantities. */
        BigDecimal freeQuantity
) {}
