package com.hardware.erp.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Customer 360 - what this customer has bought before (CR-030 §6). Never includes purchase/cost price - this is what the customer paid, unrelated to PRODUCT_VIEW_COST. */
public record CustomerProductHistoryResponse(
        Long productId,
        String productName,
        String productCode,
        String unit,
        BigDecimal totalQuantityPurchased,
        String lastPriceDisplay,
        LocalDate lastPurchaseDate
) {}
