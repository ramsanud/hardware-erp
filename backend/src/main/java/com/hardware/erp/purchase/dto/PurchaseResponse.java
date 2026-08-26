package com.hardware.erp.purchase.dto;

import com.hardware.erp.purchase.entity.PurchaseStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseResponse(
        Long id,
        String purchaseNumber,
        Long supplierId,
        String supplierName,
        String supplierMobile,
        String supplierBillNumber,
        LocalDate purchaseDate,
        String subtotalDisplay,
        String gstAmountDisplay,
        String totalDisplay,
        String paidDisplay,
        String balanceDisplay,
        PurchaseStatus status,
        String remarks,
        boolean imported,
        LocalDateTime importedAt,
        List<PurchaseItemResponse> items,
        List<PurchasePaymentResponse> payments,
        boolean hasDocument,
        LocalDateTime createdAt
) {}
