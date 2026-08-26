package com.hardware.erp.purchase.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecordPurchasePaymentRequest(
        @NotNull @Positive Long amountPaise,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 255) String notes
) {}
