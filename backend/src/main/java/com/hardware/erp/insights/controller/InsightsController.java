package com.hardware.erp.insights.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.insights.dto.InsightsDtos.BoughtTogetherResponse;
import com.hardware.erp.insights.dto.InsightsDtos.DemandTrendResponse;
import com.hardware.erp.insights.dto.InsightsDtos.OverstockResponse;
import com.hardware.erp.insights.dto.InsightsDtos.PricingInsightResponse;
import com.hardware.erp.insights.dto.InsightsDtos.ReorderResponse;
import com.hardware.erp.insights.dto.InsightsDtos.SlowMovingResponse;
import com.hardware.erp.insights.service.InsightsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-092. REPORT_VIEW (it is a report) plus the SMART_INSIGHTS plan gate
 * inside the service. Pricing exposes cost and margin, so it additionally
 * needs PRODUCT_VIEW_COST - the same line STAFF is kept behind everywhere.
 */
@RestController
@RequestMapping("/v1/insights")
@RequiredArgsConstructor
@Tag(name = "Smart Insights")
public class InsightsController {

    private final InsightsService insightsService;

    @GetMapping("/slow-moving")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    public ApiResponse<SlowMovingResponse> slowMoving(@RequestParam(defaultValue = "90") int days) {
        return ApiResponse.ok(insightsService.slowMoving(days));
    }

    @GetMapping("/overstock")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    public ApiResponse<OverstockResponse> overstock(@RequestParam(defaultValue = "90") int days,
                                                    @RequestParam(defaultValue = "120") int coverDays) {
        return ApiResponse.ok(insightsService.overstock(days, coverDays));
    }

    @GetMapping("/reorder")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    public ApiResponse<ReorderResponse> reorder(@RequestParam(defaultValue = "30") int days,
                                                @RequestParam(defaultValue = "7") int leadTimeDays) {
        return ApiResponse.ok(insightsService.reorder(days, leadTimeDays));
    }

    @GetMapping("/demand-trend")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    public ApiResponse<DemandTrendResponse> demandTrend(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(insightsService.demandTrend(days));
    }

    @GetMapping("/bought-together")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
    public ApiResponse<BoughtTogetherResponse> boughtTogether(@RequestParam(defaultValue = "90") int days) {
        return ApiResponse.ok(insightsService.boughtTogether(days));
    }

    @GetMapping("/pricing")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW) and hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PRODUCT_VIEW_COST)")
    public ApiResponse<PricingInsightResponse> pricing(@RequestParam(defaultValue = "90") int days) {
        return ApiResponse.ok(insightsService.pricing(days));
    }
}
