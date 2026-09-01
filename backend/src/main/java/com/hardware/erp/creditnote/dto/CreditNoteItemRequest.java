package com.hardware.erp.creditnote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * invoiceItemId, not productId - see CreditNoteItem's header comment for
 * why a product id would be ambiguous (BUG-FE-021).
 */
public record CreditNoteItemRequest(
        @NotNull Long invoiceItemId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity
) {}
