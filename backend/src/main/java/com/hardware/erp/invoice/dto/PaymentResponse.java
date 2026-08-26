package com.hardware.erp.invoice.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String amountDisplay,
        PaymentMethod paymentMethod,
        LocalDateTime paymentDate,
        String notes
) {}
