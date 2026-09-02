package com.hardware.erp.invoice.dto;

import com.hardware.erp.common.util.LineDiscount;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Price and GST are never taken from the request - they are read from the
 * product master at the moment of sale and snapshotted, so a tampered price
 * in the request body cannot under-charge an invoice.
 *
 * The discount fields (CR-047) are the deliberate exception: a negotiated
 * discount has no source other than the owner, so it does arrive from the
 * client - and is therefore re-validated server-side by
 * {@link LineDiscount#price}, which is the only place a line total is ever
 * computed. The bean-validation limits below are the cheap first pass; the
 * rule that actually cannot be expressed here - "a fixed discount may not
 * exceed this line's gross" - needs the quantity and the master price
 * together and is enforced in LineDiscount.
 *
 * All three discount fields are nullable. An older client that posts only
 * productId and quantity still deserialises, and null discountType is read as
 * NONE - so this is an additive, backward-compatible change.
 */
public record InvoiceItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity,

        LineDiscount.Type discountType,

        @PositiveOrZero(message = "Discount percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "Discount cannot be more than 100%")
        BigDecimal discountPercent,

        /**
         * CR-050 internal labour margin, percentage of the discounted value.
         * Owner-only: it changes the rate but never appears as its own line on
         * a customer document.
         */
        @PositiveOrZero(message = "Labour percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "Labour cannot be more than 100%")
        BigDecimal labourPercent,

        /**
         * CR-053 backlog item 1. Bonus units given free, over and above
         * quantity - never priced, but deducted from stock alongside
         * quantity (see InvoiceServiceImpl). Null is read as zero, same
         * convention as the discount fields.
         */
        @PositiveOrZero(message = "Free quantity cannot be negative")
        BigDecimal freeQuantity
) {
    /** Convenience for callers that never set a discount - keeps existing construction sites short. */
    public InvoiceItemRequest(Long productId, BigDecimal quantity) {
        this(productId, quantity, LineDiscount.Type.NONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
