package com.hardware.erp.purchase.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;

import java.time.LocalDateTime;

public record PurchasePaymentResponse(
        Long id,
        String amountDisplay,
        PaymentMethod paymentMethod,
        LocalDateTime paymentDate,
        String notes
) {}
