package com.hardware.erp.labour.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.labour.entity.WorkerPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WorkerPaymentResponse(
        Long id,
        Long workerId,
        String workerName,
        Long amountPaise,
        String amountDisplay,
        LocalDate paymentDate,
        PaymentMethod paymentMethod,
        String notes,
        WorkerPaymentStatus status,
        LocalDateTime createdAt
) {}
