package com.hardware.erp.salesorder.dto;

import com.hardware.erp.common.util.LineDiscount;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Mirrors QuotationItemRequest field for field - see its header comment for why. */
public record SalesOrderItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
        BigDecimal quantity,

        LineDiscount.Type discountType,

        @PositiveOrZero(message = "Discount percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "Discount cannot be more than 100%")
        BigDecimal discountPercent,

        @PositiveOrZero(message = "Labour percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "Labour cannot be more than 100%")
        BigDecimal labourPercent
) {
    public SalesOrderItemRequest(Long productId, BigDecimal quantity) {
        this(productId, quantity, LineDiscount.Type.NONE, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
