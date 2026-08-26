package com.hardware.erp.invoice.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;

import java.time.LocalDateTime;

/** One row of the cross-invoice payment list at GET /v1/payments. */
public record PaymentSummaryResponse(
        Long id,
        Long invoiceId,
        String invoiceNumber,
        String customerName,
        String customerMobile,
        Long amountPaise,
        String amountDisplay,
        PaymentMethod paymentMethod,
        LocalDateTime paymentDate,
        String notes
) {}
