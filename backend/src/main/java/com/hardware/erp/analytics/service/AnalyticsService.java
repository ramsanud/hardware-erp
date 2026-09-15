package com.hardware.erp.analytics.service;

import com.hardware.erp.analytics.dto.AnalyticsDtos.ActivityMatrix;
import com.hardware.erp.analytics.dto.AnalyticsDtos.CategoryBreakdown;
import com.hardware.erp.analytics.dto.AnalyticsDtos.LowStockTrend;
import com.hardware.erp.analytics.dto.AnalyticsDtos.ProductPerformanceList;
import com.hardware.erp.analytics.dto.AnalyticsDtos.Summary;
import com.hardware.erp.analytics.dto.AnalyticsDtos.TrendSeries;
import com.hardware.erp.analytics.dto.AnalyticsDtos.ValueDistribution;

import java.time.LocalDate;

/**
 * Read-only aggregations over the tenant's own invoices (CR-048).
 *
 * Every method takes a date range and nothing else that identifies a tenant -
 * the tenant is always resolved from the authenticated caller inside the
 * implementation, never accepted as a parameter, so there is no signature
 * here that a caller could use to ask about someone else's shop.
 */
public interface AnalyticsService {

    Summary summary(LocalDate from, LocalDate to);

    /** granularity: day | week | month. Anything else is rejected. */
    TrendSeries revenueTrend(LocalDate from, LocalDate to, String granularity);

    /**
     * CR-084. The last {@code days} daily low-stock counts for the caller's
     * tenant. Takes today's snapshot first if none exists yet, so the series
     * always ends on a real point for today.
     */
    LowStockTrend lowStockTrend(int days);

    CategoryBreakdown salesByCategory(LocalDate from, LocalDate to);

    CategoryBreakdown paymentMethods(LocalDate from, LocalDate to);

    CategoryBreakdown invoiceStatusSplit(LocalDate from, LocalDate to);

    ProductPerformanceList topProducts(LocalDate from, LocalDate to, int limit);

    ValueDistribution invoiceValueDistribution(LocalDate from, LocalDate to, int buckets);

    ActivityMatrix salesActivity(LocalDate from, LocalDate to);
}
