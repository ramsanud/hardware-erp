package com.hardware.erp.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * quantityChange is signed - a positive number adds stock, a negative
 * number removes it. Zero is rejected: there is nothing to record.
 */
public record StockAdjustmentRequest(
        @NotNull BigDecimal quantityChange,
        @Size(max = 255) String notes
) {}
