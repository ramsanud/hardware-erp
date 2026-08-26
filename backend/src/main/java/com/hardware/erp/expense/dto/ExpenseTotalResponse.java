package com.hardware.erp.expense.dto;

/** Backs the ledger page's running total for whatever date range is currently filtered. */
public record ExpenseTotalResponse(
        long totalAmountPaise,
        String totalAmountDisplay
) {}
