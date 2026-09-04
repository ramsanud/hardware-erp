package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.PlatformDashboardResponse;
import com.hardware.erp.platformadmin.service.PlatformAdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No @PreAuthorize permission gate beyond being an authenticated platform
 * admin (enforced by PlatformAdminSecurityConfig's own filter chain) - these
 * are aggregate counts, not one tenant's business data, and every platform
 * staff role should see what is happening on the platform on login. Compare
 * to /v1/platform-admin/auth/me, which has the same "authenticated is
 * enough" shape.
 */
@RestController
@RequestMapping("/v1/platform-admin/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Dashboard", description = "Real-time platform-wide KPIs")
public class PlatformAdminDashboardController {

    private final PlatformAdminDashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Admin Overview KPIs",
            description = "Tenants, users, today's business activity and subscription mix - "
                        + "every figure is a live database aggregate.")
    public ResponseEntity<ApiResponse<PlatformDashboardResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.overview()));
    }
}
