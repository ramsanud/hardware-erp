package com.hardware.erp.coupon.entity;

public enum DiscountType {
    /** discountValue is a 0-100 percentage of the eligible subtotal, optionally capped by maxDiscountPaise. */
    PERCENT,
    /** discountValue is a flat amount in paise, never more than the eligible subtotal. */
    FLAT
}
