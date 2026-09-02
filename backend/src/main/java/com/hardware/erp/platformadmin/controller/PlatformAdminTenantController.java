package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.platformadmin.dto.PlatformTenantDetailResponse;
import com.hardware.erp.platformadmin.dto.PlatformTenantSummaryResponse;
import com.hardware.erp.platformadmin.dto.SuspendTenantRequest;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.PlatformAdminTenantService;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.TenantStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Platform Admin Console, phase 2 - the highest-priority module per the
 * spec. TENANT_VIEW/TENANT_MANAGE are enforced here, not just hidden in the
 * frontend - see PlatformAdminRole for which of the 7 roles hold each.
 */
@RestController
@RequestMapping("/v1/platform-admin/tenants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Tenants", description = "Cross-tenant management: list, detail, suspend, reactivate")
public class PlatformAdminTenantController {

    private final PlatformAdminTenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_VIEW')")
    @Operation(summary = "Search/list tenants",
            description = "Filters: free-text search (name/slug/email/phone), status, subscription tier.")
    public ResponseEntity<ApiResponse<PageResponse<PlatformTenantSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) SubscriptionTier tier,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.list(search, status, tier, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TENANT_VIEW')")
    @Operation(summary = "Tenant detail: profile, usage counts, subscription, WhatsApp connection status")
    public ResponseEntity<ApiResponse<PlatformTenantDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.get(id)));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('TENANT_MANAGE')")
    @Operation(summary = "Suspend a tenant",
            description = "Blocks every user of this shop from signing in, without touching their rows. "
                        + "Requires a reason; every suspension is written to the platform audit log.")
    public ResponseEntity<ApiResponse<PlatformTenantSummaryResponse>> suspend(
            @PathVariable Long id, @Valid @RequestBody SuspendTenantRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                tenantService.suspend(id, request.reason(), currentAdminId(), httpRequest)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('TENANT_MANAGE')")
    @Operation(summary = "Reactivate a suspended tenant")
    public ResponseEntity<ApiResponse<PlatformTenantSummaryResponse>> reactivate(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(
                tenantService.reactivate(id, currentAdminId(), httpRequest)));
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
