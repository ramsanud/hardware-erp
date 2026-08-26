package com.hardware.erp.coupon.dto;

import com.hardware.erp.coupon.entity.CouponStatus;
import com.hardware.erp.coupon.entity.DiscountType;

import java.math.BigDecimal;

public record CouponSummaryResponse(
        Long id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        Integer usageLimit,
        int timesUsed,
        CouponStatus status,
        boolean currentlyValid,
        boolean restrictedToProducts
) {}
