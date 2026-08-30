package com.hardware.erp.analytics.controller;

import com.hardware.erp.analytics.dto.AnalyticsDtos.*;
import com.hardware.erp.analytics.service.AnalyticsService;
import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Analytics aggregations (CR-048).
 *
 * Gated on REPORT_VIEW rather than a new permission code: this is exactly the
 * "look at the shop's numbers" capability that permission already names, and
 * the role templates that should see analytics (OWNER, MANAGER, ACCOUNTANT)
 * already hold it. Adding a second code would have meant re-granting it to
 * every existing role for no gain - and V1's OWNER grant is a one-time
 * CROSS JOIN, so a new code silently reaches nobody (see BUG-LAB-001).
 *
 * No endpoint accepts a tenant id. Every one resolves the tenant from the
 * authenticated caller inside the service, so there is no parameter here a
 * client could use to read another shop's figures.
 */
@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class AnalyticsController {

    private static final String REPORT_VIEW =
            "hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)";

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<Summary> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.summary(from, to));
    }

    @GetMapping("/revenue-trend")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<TrendSeries> revenueTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.ok(analyticsService.revenueTrend(from, to, granularity));
    }

    @GetMapping("/sales-by-category")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<CategoryBreakdown> salesByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.salesByCategory(from, to));
    }

    @GetMapping("/payment-methods")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<CategoryBreakdown> paymentMethods(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.paymentMethods(from, to));
    }

    @GetMapping("/invoice-status")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<CategoryBreakdown> invoiceStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.invoiceStatusSplit(from, to));
    }

    @GetMapping("/top-products")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<ProductPerformanceList> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(analyticsService.topProducts(from, to, limit));
    }

    @GetMapping("/invoice-value-distribution")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<ValueDistribution> invoiceValueDistribution(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int buckets) {
        return ApiResponse.ok(analyticsService.invoiceValueDistribution(from, to, buckets));
    }

    @GetMapping("/sales-activity")
    @PreAuthorize(REPORT_VIEW)
    public ApiResponse<ActivityMatrix> salesActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.salesActivity(from, to));
    }
}
