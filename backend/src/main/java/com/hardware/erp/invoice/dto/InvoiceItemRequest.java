package com.hardware.erp.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Price and GST are never taken from the request - they are read from the
 * product master at the moment of sale and snapshotted, so a tampered price
 * in the request body cannot under-charge an invoice.
 */
public record InvoiceItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity
) {}
