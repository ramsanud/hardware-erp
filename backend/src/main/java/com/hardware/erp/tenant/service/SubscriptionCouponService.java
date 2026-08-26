package com.hardware.erp.tenant.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.tenant.dto.SubscriptionCouponRedemptionResponse;
import com.hardware.erp.tenant.dto.SubscriptionCouponRequest;
import com.hardware.erp.tenant.dto.SubscriptionCouponResponse;
import com.hardware.erp.tenant.entity.SubscriptionCouponStatus;
import org.springframework.data.domain.Pageable;

/** CR-032 - plan trial coupons the OWNER creates and redeems for their own tenant. See SubscriptionCoupon for the full design note. */
public interface SubscriptionCouponService {

    SubscriptionCouponResponse create(SubscriptionCouponRequest request);

    SubscriptionCouponResponse update(Long id, SubscriptionCouponRequest request);

    PageResponse<SubscriptionCouponResponse> search(String search, SubscriptionCouponStatus status, Pageable pageable);

    void delete(Long id);

    SubscriptionCouponRedemptionResponse redeem(String code);
}
