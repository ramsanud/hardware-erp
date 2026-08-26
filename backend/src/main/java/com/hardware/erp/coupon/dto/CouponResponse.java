package com.hardware.erp.coupon.dto;

import com.hardware.erp.coupon.entity.CouponStatus;
import com.hardware.erp.coupon.entity.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CouponResponse(
        Long id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        Long minPurchasePaise,
        String minPurchaseDisplay,
        Long maxDiscountPaise,
        String maxDiscountDisplay,
        LocalDate validFrom,
        LocalDate validUntil,
        Integer usageLimit,
        int timesUsed,
        CouponStatus status,
        boolean currentlyValid,
        List<ProductRef> products
) {
    public record ProductRef(Long id, String productCode, String productName) {}
}
