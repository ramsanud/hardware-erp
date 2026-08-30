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
    public record TrendPoint(
            String bucket,
            long revenuePaise,
            String revenueDisplay,
            long invoiceCount
    ) {}

    public record TrendSeries(Period period, List<TrendPoint> points, String summary) {}

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
}
