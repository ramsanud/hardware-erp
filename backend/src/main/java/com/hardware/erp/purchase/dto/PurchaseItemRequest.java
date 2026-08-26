package com.hardware.erp.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * unitPricePaise is taken from the request, unlike InvoiceItemRequest -
 * a purchase price is exactly what the supplier charged on this bill,
 * which is real information the caller has and the system does not (the
 * product master's purchasePricePaise is the *previous* purchase price,
 * not this one - PurchaseServiceImpl decides whether to update it).
 */
public record PurchaseItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity,
        @NotNull @Min(value = 0, message = "Unit price cannot be negative")
        Long unitPricePaise,
        @NotNull @DecimalMin(value = "0", message = "GST rate cannot be negative")
        BigDecimal gstRatePercent
) {}
