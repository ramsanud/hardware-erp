package com.hardware.erp.coupon.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.coupon.dto.CouponRequest;
import com.hardware.erp.coupon.dto.CouponResponse;
import com.hardware.erp.coupon.dto.CouponSummaryResponse;
import com.hardware.erp.coupon.entity.CouponStatus;
import com.hardware.erp.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons")
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).COUPON_VIEW)")
    public ApiResponse<PageResponse<CouponSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CouponStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(couponService.search(search, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).COUPON_VIEW)")
    public ApiResponse<CouponResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(couponService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).COUPON_MANAGE)")
    public ApiResponse<CouponResponse> create(@Valid @RequestBody CouponRequest request) {
        return ApiResponse.ok(couponService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).COUPON_MANAGE)")
    public ApiResponse<CouponResponse> update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return ApiResponse.ok(couponService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).COUPON_MANAGE)")
    public void delete(@PathVariable Long id) {
        couponService.delete(id);
    }
}
