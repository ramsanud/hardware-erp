package com.hardware.erp.billing.controller;

import com.hardware.erp.billing.dto.CreateSubscriptionOrderRequest;
import com.hardware.erp.billing.dto.SubscriptionOrderResponse;
import com.hardware.erp.billing.dto.TenantBillingHistoryResponse;
import com.hardware.erp.billing.dto.VerifyPaymentRequest;
import com.hardware.erp.billing.service.SubscriptionBillingService;
import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * CR-057 phase 9 - real Razorpay Orders-API checkout to move a tenant's own
 * plan up. Gated by SETTINGS_MANAGE, same as every other shop-wide billing-
 * adjacent action (SubscriptionCouponController, bank accounts).
 */
@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing")
public class SubscriptionBillingController {

    private final SubscriptionBillingService billingService;

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<SubscriptionOrderResponse> checkout(@Valid @RequestBody CreateSubscriptionOrderRequest request) {
        return ApiResponse.ok(billingService.createOrder(request.requestedTier()));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyPaymentRequest request) {
        billingService.verifyPayment(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<TenantBillingHistoryResponse> history() {
        return ApiResponse.ok(billingService.history());
    }
}
