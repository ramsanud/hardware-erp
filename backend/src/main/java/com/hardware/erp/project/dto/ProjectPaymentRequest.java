package com.hardware.erp.project.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "ProjectPaymentRequest")
public record ProjectPaymentRequest(
        @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be greater than zero")
        Long amountPaise,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "Date is required")
        LocalDate paymentDate,

        @Size(max = 500)
        String notes
) {}
