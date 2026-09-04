package com.hardware.erp.platformadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Admin Overview KPIs. Every field here is a real database aggregate,
 * computed on request - see PlatformAdminDashboardService. Nothing here is
 * Math.random() or a hardcoded placeholder (Platform Admin spec section
 * 62). System health / error rate / background job health are Phase 3
 * (System Health Center) and deliberately not faked here - see
 * platformHealth for the one thing this phase can honestly report.
 */
@Schema(name = "PlatformDashboardResponse")
public record PlatformDashboardResponse(
        Tenants tenants,
        Users users,
        BusinessActivityToday businessActivityToday,
        Subscriptions subscriptions,
        PlatformHealth platformHealth,
        LocalDateTime generatedAt
) {
    public record Tenants(
            long total,
            long active,
            long suspended,
            long newThisMonth,
            /** Null when last month had zero new tenants - a percentage against zero is not a real number. */
            Double growthPercentVsLastMonth
    ) {}

    public record Users(
            long total,
            long active,
            long newToday
    ) {}

    /** Counted platform-wide, not per tenant - "how much is happening on Hardware ERP today". */
    public record BusinessActivityToday(
            long invoices,
            long payments,
            long purchases
    ) {}

    public record Subscriptions(
            long free,
            long pro,
            long max
    ) {}

    /**
     * The only "health" this phase can honestly report: this response
     * existing at all proves the API and the database answered. Real
     * per-service status, response times and error rates are Phase 3.
     */
    public record PlatformHealth(
            boolean databaseReachable
    ) {}
}
