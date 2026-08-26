package com.hardware.erp.labour.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record WorkerPaymentRequest(
        @NotNull Long workerId,
        @NotNull @Positive Long amountPaise,
        /** A payment records cash that has already changed hands, never a promise of a future one. */
        @NotNull @PastOrPresent LocalDate paymentDate,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 500) String notes
) {}
