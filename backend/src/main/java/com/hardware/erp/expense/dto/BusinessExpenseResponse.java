package com.hardware.erp.expense.dto;

import com.hardware.erp.expense.entity.ExpenseStatus;
import com.hardware.erp.invoice.entity.PaymentMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BusinessExpenseResponse(
        Long id,
        LocalDate expenseDate,
        Long categoryId,
        String categoryName,
        Long amountPaise,
        String amountDisplay,
        PaymentMethod paymentMethod,
        String notes,
        ExpenseStatus status,
        boolean hasReceipt,
        LocalDateTime createdAt
) {}
