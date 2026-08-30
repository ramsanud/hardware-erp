package com.hardware.erp.quotation.dto;

import com.hardware.erp.common.util.LineDiscount;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Mirrors {@link com.hardware.erp.invoice.dto.InvoiceItemRequest} field for
 * field, because a quotation converts into an invoice and any divergence
 * between the two shapes becomes a discount silently lost at conversion
 * (CR-047).
 *
 * All three discount fields are nullable, so an older client posting only
 * productId and quantity still deserialises and is read as no discount.
 */
public record QuotationItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity,

        LineDiscount.Type discountType,

        @PositiveOrZero(message = "Discount percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "Discount cannot be more than 100%")
        BigDecimal discountPercent,

        @PositiveOrZero(message = "Discount amount cannot be negative")
        Long discountAmountPaise
) {
    public QuotationItemRequest(Long productId, BigDecimal quantity) {
        this(productId, quantity, LineDiscount.Type.NONE, BigDecimal.ZERO, 0L);
    }
}
