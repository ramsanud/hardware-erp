package com.hardware.erp.coupon.dto;

import com.hardware.erp.coupon.entity.CouponStatus;
import com.hardware.erp.coupon.entity.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CouponRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Use only letters, digits and hyphens")
        String code,
        @Size(max = 255) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal discountValue,
        @PositiveOrZero Long minPurchasePaise,
        @PositiveOrZero Long maxDiscountPaise,
        LocalDate validFrom,
        LocalDate validUntil,
        @Positive Integer usageLimit,
        @NotNull CouponStatus status,
        /** Empty/null = every product is eligible. */
        List<Long> productIds
) {}
