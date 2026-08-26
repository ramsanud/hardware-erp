package com.hardware.erp.purchase.dto;

public record ImportResultResponse(
        Long purchaseId,
        String purchaseNumber,
        int rowsImported,
        int existingProductsMatched,
        int newProductsCreated,
        /** Rows that named the exact same new product as an earlier row in this same bill - counted separately from existingProductsMatched, which is only genuine pre-existing-catalogue matches (spec §22's summary must not blur the two). */
        int rowsMergedWithEarlierRow,
        String stockAddedDisplay,
        String totalPurchaseDisplay
) {}
