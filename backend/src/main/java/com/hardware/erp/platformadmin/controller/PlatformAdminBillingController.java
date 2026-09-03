package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.billing.dto.PlatformBillingOverviewResponse;
import com.hardware.erp.billing.dto.TenantBillingHistoryResponse;
import com.hardware.erp.billing.service.PlatformBillingQueryService;
import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CR-057 phase 9 - BILLING_VIEW is enforced here, not just hidden in the frontend. See PlatformAdminRole for who holds it. */
@RestController
@RequestMapping("/v1/platform-admin/billing")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Billing", description = "Cross-tenant revenue overview and per-tenant subscription/payment history")
public class PlatformAdminBillingController {

    private final PlatformBillingQueryService billingQueryService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('BILLING_VIEW')")
    @Operation(summary = "Revenue chart data - last 12 months, aggregated server-side")
    public ResponseEntity<ApiResponse<PlatformBillingOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(billingQueryService.overview()));
    }

    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAuthority('BILLING_VIEW')")
    @Operation(summary = "One tenant's current plan + payment history")
    public ResponseEntity<ApiResponse<TenantBillingHistoryResponse>> tenantHistory(@PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(billingQueryService.tenantHistory(tenantId)));
    }
}
