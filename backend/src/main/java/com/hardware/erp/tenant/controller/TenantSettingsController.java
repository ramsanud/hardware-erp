package com.hardware.erp.tenant.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.tenant.dto.TenantBrandResponse;
import com.hardware.erp.tenant.dto.TenantSettingsRequest;
import com.hardware.erp.tenant.dto.TenantSettingsResponse;
import com.hardware.erp.tenant.dto.UsageSummaryResponse;
import com.hardware.erp.tenant.service.EntitlementService;
import com.hardware.erp.tenant.service.TenantSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings")
public class TenantSettingsController {

    private final TenantSettingsService settingsService;
    private final EntitlementService entitlementService;

    @GetMapping("/brand")
    public ApiResponse<TenantBrandResponse> brand() {
        TenantSettingsResponse settings = settingsService.get();
        return ApiResponse.ok(new TenantBrandResponse(settings.name(), settings.hasLogo(), settings.subscriptionTier(),
                settings.showPriceHistory(), settings.enableFreeQuantity(),
                settings.tdsEnabled(), settings.tdsRatePercent(), settings.tcsEnabled(), settings.tcsRatePercent(),
                settings.einvoiceEnabled()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<TenantSettingsResponse> get() {
        return ApiResponse.ok(settingsService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_MANAGE)")
    public ApiResponse<TenantSettingsResponse> update(@Valid @RequestBody TenantSettingsRequest request) {
        return ApiResponse.ok(settingsService.update(request));
    }

    /** CR-031 (Customer 360 §27-40) - usage against the tenant's subscription-tier entitlement limits, for the Shop Settings usage dashboard. */
    @GetMapping("/usage")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).SETTINGS_VIEW)")
    public ApiResponse<UsageSummaryResponse> usage() {
        return ApiResponse.ok(entitlementService.usageSummary());
    }
}
