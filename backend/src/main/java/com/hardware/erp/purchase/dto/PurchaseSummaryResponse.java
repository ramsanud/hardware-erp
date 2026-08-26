package com.hardware.erp.purchase.dto;

import com.hardware.erp.purchase.entity.PurchaseStatus;

import java.time.LocalDate;

public record PurchaseSummaryResponse(
        Long id,
        String purchaseNumber,
        String supplierName,
        String supplierBillNumber,
        LocalDate purchaseDate,
        String totalDisplay,
        String balanceDisplay,
        PurchaseStatus status,
        boolean imported
) {}
