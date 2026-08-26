package com.hardware.erp.expense.dto;

import com.hardware.erp.invoice.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BusinessExpenseRequest(
        @NotNull LocalDate expenseDate,
        @NotNull Long categoryId,
        @NotNull @Positive Long amountPaise,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 500) String notes
) {}
