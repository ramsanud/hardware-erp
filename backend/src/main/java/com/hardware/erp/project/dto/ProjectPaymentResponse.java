package com.hardware.erp.project.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;

import java.time.LocalDate;

public record ProjectPaymentResponse(
        Long id,
        String amountDisplay,
        PaymentMethod paymentMethod,
        LocalDate paymentDate,
        String notes
) {}
