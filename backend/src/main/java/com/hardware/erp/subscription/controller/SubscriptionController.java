package com.hardware.erp.subscription.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.subscription.dto.CancelSubscriptionRequest;
import com.hardware.erp.subscription.dto.ChangePlanRequest;
import com.hardware.erp.subscription.dto.CurrentSubscriptionResponse;
import com.hardware.erp.subscription.dto.SubscriptionPlanResponse;
import com.hardware.erp.subscription.dto.UsageResponse;
import com.hardware.erp.subscription.service.SubscriptionLifecycleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CR-088 §24. /plans is public (the pricing page is shown before login);
 * everything about THIS shop's own subscription requires an authenticated
 * user - any authenticated role may view its own plan/usage, but only
 * SETTINGS_MANAGE may change it, mirroring TenantSettingsController.
 */
@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionLifecycleService lifecycleService;

    @GetMapping("/plans")
    public ApiResponse<List<SubscriptionPlanResponse>> plans() {
        return ApiResponse.ok(lifecycleService.plans());
    }

    @GetMapping("/current")
    public ApiResponse<CurrentSubscriptionResponse> current() {
        return ApiResponse.ok(lifecycleService.current());
    }

    @GetMapping("/features")
    public ApiResponse<List<String>> features() {
        return ApiResponse.ok(lifecycleService.current().featureKeys());
    }

    @GetMapping("/usage")
    public ApiResponse<UsageResponse> usage() {
        return ApiResponse.ok(lifecycleService.current().usage());
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<CurrentSubscriptionResponse> upgrade(@Valid @RequestBody ChangePlanRequest request) {
        return ApiResponse.ok("Plan updated", lifecycleService.changePlan(request.planCode(), request.reason()));
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<CurrentSubscriptionResponse> cancel(@Valid @RequestBody CancelSubscriptionRequest request) {
        return ApiResponse.ok("Subscription cancelled - your data is kept", lifecycleService.cancel(request.reason()));
    }
}
