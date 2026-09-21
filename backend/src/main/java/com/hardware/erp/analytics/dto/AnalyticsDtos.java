package com.hardware.erp.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Analytics response shapes (CR-048), grouped in one file because each is a
 * few lines and they are only ever used together by AnalyticsController.
 *
 * Every money field appears TWICE: once as raw paise for the chart to plot,
 * and once as a formatted display string for tooltips, axis labels and the
 * accessible text alternative. The frontend must never format money itself -
 * Indian digit grouping (1,50,000 not 150,000) lives in IndianCurrencyFormat
 * and is not something to reimplement in TypeScript.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** The window every response was computed over, echoed back so a chart can label itself honestly. */
    public record Period(LocalDate from, LocalDate to, String granularity) {}

    public record Summary(
            Period period,
            long revenuePaise,
            String revenueDisplay,
            long invoiceCount,
            long averageOrderValuePaise,
            String averageOrderValueDisplay,
            long outstandingPaise,
            String outstandingDisplay
    ) {}

    /** One point on the revenue line. bucket is an ISO date - the start of the day/week/month. */
    /**
     * CR-084 added outstandingPaise: the balance still due on the invoices in
     * this bucket - the same rows, the same "not cancelled" rule, one more
     * column. It is what the dashboard's Pending Payments sparkline draws.
     */
    public record TrendPoint(
            String bucket,
            long revenuePaise,
            String revenueDisplay,
            long invoiceCount,
            long outstandingPaise,
            String outstandingDisplay
    ) {}

    public record TrendSeries(Period period, List<TrendPoint> points, String summary) {}

    /** CR-084. One day's low-stock count, from low_stock_snapshot. */
    public record LowStockPoint(LocalDate date, int lowStockCount) {}

    /**
     * CR-084. Daily low-stock counts for the last {@code days}, oldest first.
     * Only days with a snapshot appear; a shop that went live yesterday has
     * one point, not a fortnight of zeros pretending to be history.
     */
    public record LowStockTrend(List<LowStockPoint> points, String summary) {}

    /** A bar, or a donut segment. share is 0-100, computed server-side so every client agrees. */
    public record CategorySlice(
            String label,
            long amountPaise,
            String amountDisplay,
            BigDecimal quantity,
            BigDecimal sharePercent
    ) {}

    public record CategoryBreakdown(Period period, List<CategorySlice> slices, String summary) {}

    /**
     * Carries unit price as well as quantity so the same payload backs both
     * the top-products bar chart and the price-vs-quantity scatter. Two
     * charts from one query cannot disagree with each other.
     */
    public record ProductPerformance(
            long productId,
            String label,
            long amountPaise,
            String amountDisplay,
            BigDecimal quantity,
            long unitPricePaise,
            String unitPriceDisplay
    ) {}

    public record ProductPerformanceList(Period period, List<ProductPerformance> products, String summary) {}

    /**
     * One histogram bin. {@code overflow} marks the below-floor and
     * above-ceiling bins, which are kept rather than dropped - on a hardware
     * shop's data the outliers are the big project invoices, the ones most
     * worth seeing.
     */
    public record ValueBucket(
            String label,
            long fromPaise,
            long toPaise,
            long invoiceCount,
            boolean overflow
    ) {}

    public record ValueDistribution(Period period, List<ValueBucket> buckets, String summary) {}

    /** dayOfWeek is 0=Sunday..6=Saturday (PostgreSQL's dow); the client supplies the labels. */
    public record ActivityCell(
            int dayOfWeek,
            int hourOfDay,
            long invoiceCount,
            long revenuePaise,
            String revenueDisplay
    ) {}

    public record ActivityMatrix(Period period, List<ActivityCell> cells, long peakInvoiceCount, String summary) {}

    /**
     * CR-091 Phase 6. Revenue and COGS from real recorded rows only - never
     * fabricated (hard rule 12). COGS is the SUM of invoice_item
     * .cost_price_paise (the weighted-average cost frozen at sale), not
     * "selling price minus today's purchase price" - a later price change
     * never rewrites an old sale's margin. Sales returns reduce both revenue
     * and COGS by the returned lines' own frozen figures; cancelled invoices
     * are excluded entirely, matching every other analytics query.
     */
    public record ProfitResponse(
            Period period,
            long revenuePaise, String revenueDisplay,
            long salesReturnPaise, String salesReturnDisplay,
            long netRevenuePaise, String netRevenueDisplay,
            long cogsPaise, String cogsDisplay,
            long grossProfitPaise, String grossProfitDisplay,
            long expensePaise, String expenseDisplay,
            long netProfitPaise, String netProfitDisplay
    ) {}
}
