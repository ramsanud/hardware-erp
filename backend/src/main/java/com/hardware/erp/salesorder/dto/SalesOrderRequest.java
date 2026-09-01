package com.hardware.erp.salesorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import com.hardware.erp.common.util.LineDiscount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderRequest(
        @NotBlank @Size(max = 255) String customerName,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit mobile number")
        String customerMobile,
        @Email @Size(max = 255) String customerEmail,
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "Enter a valid 15-character GSTIN")
        String customerGstNo,
        @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code is the 2-digit GST state code")
        String customerStateCode,
        /** Informational only - unlike Quotation's validUntil, nothing gates on this. */
        LocalDate expectedDeliveryDate,
        @NotEmpty @Valid List<SalesOrderItemRequest> items,

        /** CR-052, mirrors Quotation's whole-document discount (CR-049). */
        LineDiscount.Type orderDiscountType,

        @PositiveOrZero(message = "Discount percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "Discount cannot be more than 100%")
        BigDecimal orderDiscountPercent,
        @Size(max = 500) String remarks
) {}
