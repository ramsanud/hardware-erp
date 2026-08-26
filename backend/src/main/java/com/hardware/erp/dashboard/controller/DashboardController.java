package com.hardware.erp.dashboard.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.dashboard.dto.SalesSummaryResponse;
import com.hardware.erp.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/sales-summary")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).INVOICE_VIEW)")
    public ApiResponse<SalesSummaryResponse> salesSummary() {
        return ApiResponse.ok(dashboardService.salesSummary());
    }
}
