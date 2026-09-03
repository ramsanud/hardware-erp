package com.hardware.erp.billing.dto;

import java.util.List;

/**
 * Platform Admin Console dashboard's Revenue chart data (spec §3) - already
 * aggregated server-side, never raw payment rows shipped to the browser
 * (spec §26). razorpayConfigured lets the frontend show an honest "Billing
 * not configured in this environment" banner instead of a chart with real
 * zeros baked in.
 */
public record PlatformBillingOverviewResponse(
        boolean razorpayConfigured,
        long totalRevenuePaiseLast12Months,
        long successfulPaymentsLast12Months,
        long failedPaymentsLast12Months,
        List<MonthlyRevenuePoint> monthly
) {
    public record MonthlyRevenuePoint(
            String month,
            long revenuePaise,
            long successfulCount,
            long failedCount
    ) {}
}
