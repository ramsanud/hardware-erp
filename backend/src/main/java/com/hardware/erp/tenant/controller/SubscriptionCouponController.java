package com.hardware.erp.tenant.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.tenant.dto.RedeemSubscriptionCouponRequest;
import com.hardware.erp.tenant.dto.SubscriptionCouponRedemptionResponse;
import com.hardware.erp.tenant.dto.SubscriptionCouponRequest;
import com.hardware.erp.tenant.dto.SubscriptionCouponResponse;
import com.hardware.erp.tenant.entity.SubscriptionCouponStatus;
import com.hardware.erp.tenant.service.SubscriptionCouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CR-032 - plan trial coupons, gated by SETTINGS_MANAGE throughout (OWNER-only in practice, same as PUT /v1/settings). */
@RestController
@RequestMapping("/v1/subscription-coupons")
@RequiredArgsConstructor
@Tag(name = "Subscription coupons")
public class SubscriptionCouponController {

    private final SubscriptionCouponService couponService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<PageResponse<SubscriptionCouponResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionCouponStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(couponService.search(search, status, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<SubscriptionCouponResponse> create(@Valid @RequestBody SubscriptionCouponRequest request) {
        return ApiResponse.ok(couponService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<SubscriptionCouponResponse> update(@PathVariable Long id, @Valid @RequestBody SubscriptionCouponRequest request) {
        return ApiResponse.ok(couponService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public void delete(@PathVariable Long id) {
        couponService.delete(id);
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<SubscriptionCouponRedemptionResponse> redeem(@Valid @RequestBody RedeemSubscriptionCouponRequest request) {
        return ApiResponse.ok(couponService.redeem(request.code()));
    }
}
