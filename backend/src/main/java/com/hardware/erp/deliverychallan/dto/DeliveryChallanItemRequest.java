package com.hardware.erp.deliverychallan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Deliberately minimal - a challan is not a tax document, so unlike
 * InvoiceItemRequest/QuotationItemRequest there is no discount or labour
 * field. Price is read from the product master at dispatch time, purely
 * for the informational value shown on the challan.
 */
public record DeliveryChallanItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity
) {}
