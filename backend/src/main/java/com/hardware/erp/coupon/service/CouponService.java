package com.hardware.erp.coupon.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.coupon.dto.CouponDiscountResult;
import com.hardware.erp.coupon.dto.CouponRequest;
import com.hardware.erp.coupon.dto.CouponResponse;
import com.hardware.erp.coupon.dto.CouponSummaryResponse;
import com.hardware.erp.coupon.entity.CouponStatus;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface CouponService {

    CouponResponse create(CouponRequest request);

    CouponResponse update(Long id, CouponRequest request);

    CouponResponse get(Long id);

    PageResponse<CouponSummaryResponse> search(String search, CouponStatus status, Pageable pageable);

    void delete(Long id);

    /**
     * Validates the code against the given cart (productId -> that line's
     * own pre-discount subtotal in paise) and returns how much to take off
     * each eligible line - throws if the code is unknown, expired, inactive,
     * exhausted, below its minimum purchase, or (when product-restricted)
     * matches nothing in this cart. Never mutates the coupon - usage is
     * only recorded once the invoice/quotation actually saves.
     */
    CouponDiscountResult calculateDiscount(String code, Map<Long, Long> lineSubtotalPaiseByProductId);

    /** Increments timesUsed by one. Call exactly once per saved invoice/quotation that used this coupon. */
    void recordUsage(Long couponId);
}
