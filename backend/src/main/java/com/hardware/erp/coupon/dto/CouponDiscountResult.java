package com.hardware.erp.coupon.dto;

import java.util.Map;

/**
 * discountPaiseByProductId is the coupon's total discount allocated
 * proportionally across each eligible product's line subtotal (by its
 * share of the eligible subtotal) - a mixed-GST-rate cart still gets each
 * line's own tax recomputed correctly on its own reduced net price, rather
 * than one blended rate applied to everything.
 */
public record CouponDiscountResult(
        Long couponId,
        String couponCode,
        long totalDiscountPaise,
        Map<Long, Long> discountPaiseByProductId
) {}
