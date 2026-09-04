package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.SystemHealthResponse;
import com.hardware.erp.platformadmin.service.PlatformAdminSystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/platform-admin/system-health")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - System Health", description = "Live, real health checks - never a synthetic green")
public class PlatformAdminSystemHealthController {

    private final PlatformAdminSystemHealthService systemHealthService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_HEALTH_VIEW')")
    @Operation(summary = "Live health of every internally-checkable service",
            description = "Backend/Database/Authentication/Storage/WhatsApp/Email/BackgroundJobs. "
                        + "Status is computed fresh on every call; last-checked/last-failure/error-count "
                        + "come from the scheduled health-check job's own history.")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(systemHealthService.overview()));
    }
}
