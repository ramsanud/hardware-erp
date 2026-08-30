package com.hardware.erp.analytics.service.impl;

import com.hardware.erp.analytics.dto.AnalyticsDtos.*;
import com.hardware.erp.analytics.repository.AnalyticsRepository;
import com.hardware.erp.analytics.service.AnalyticsService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    /**
     * date_trunc accepts many more units than these, but a chart only ever
     * wants three. Restricting here means the value reaching the query is
     * always one of a known set, whatever a client sends.
     */
    private static final Set<String> GRANULARITIES = Set.of("day", "week", "month");

    /** A shop asking for ten years of daily points wants a report, not a chart. */
    private static final int MAX_RANGE_DAYS = 1826;
    private static final int MAX_TOP_N = 50;
    private static final int MAX_BUCKETS = 30;

    private final AnalyticsRepository repository;

    // ------------------------------------------------------------- helpers

    private Period validate(LocalDate from, LocalDate to, String granularity) {
        if (from == null || to == null) {
            throw new BusinessException("A date range is required");
        }
        if (to.isBefore(from)) {
            throw new BusinessException("The end date cannot be before the start date");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessException("Date range is too large - narrow it to five years or less");
        }
        String g = granularity == null ? "day" : granularity.toLowerCase();
        if (!GRANULARITIES.contains(g)) {
            throw new BusinessException("Granularity must be day, week or month");
        }
        return new Period(from, to, g);
    }

    private static String rupees(long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }

    private static BigDecimal share(long part, long whole) {
        if (whole <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- KPIs

    @Override
    public Summary summary(LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");

        var row = repository.summary(tenantId, from, to);
        long revenue = row == null || row.getRevenuePaise() == null ? 0L : row.getRevenuePaise();
        long count = row == null || row.getInvoiceCount() == null ? 0L : row.getInvoiceCount();
        long outstanding = row == null || row.getOutstandingPaise() == null ? 0L : row.getOutstandingPaise();
        // Integer division on purpose - an average order value is money, and
        // fractions of a paise are not.
        long aov = count == 0 ? 0L : revenue / count;

        return new Summary(period, revenue, rupees(revenue), count,
                aov, rupees(aov), outstanding, rupees(outstanding));
    }

    // ------------------------------------------------------- revenue trend

    @Override
    public TrendSeries revenueTrend(LocalDate from, LocalDate to, String granularity) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, granularity);

        List<TrendPoint> points = repository.revenueTrend(tenantId, from, to, period.granularity())
                .stream()
                .map(r -> new TrendPoint(r.getBucket(), r.getRevenuePaise(),
                        rupees(r.getRevenuePaise()), r.getInvoiceCount()))
                .toList();

        return new TrendSeries(period, points, trendSummary(points));
    }

    /**
     * The accessible alternative, built server-side so screen-reader users get
     * the same conclusion a sighted user draws from the line's shape rather
     * than a list of coordinates.
     */
    private String trendSummary(List<TrendPoint> points) {
        if (points.isEmpty()) {
            return "No sales in this period.";
        }
        TrendPoint first = points.get(0);
        TrendPoint last = points.get(points.size() - 1);
        TrendPoint peak = points.stream()
                .max((a, b) -> Long.compare(a.revenuePaise(), b.revenuePaise())).orElse(first);
        long total = points.stream().mapToLong(TrendPoint::revenuePaise).sum();

        String direction = last.revenuePaise() > first.revenuePaise() ? "rose"
                : last.revenuePaise() < first.revenuePaise() ? "fell" : "held steady";

        return "Revenue " + direction + " from " + first.revenueDisplay() + " on " + first.bucket()
                + " to " + last.revenueDisplay() + " on " + last.bucket()
                + ". Peak " + peak.revenueDisplay() + " on " + peak.bucket()
                + ". Total " + rupees(total) + " across " + points.size() + " periods.";
    }

    // ------------------------------------------------------- breakdowns

    private CategoryBreakdown breakdown(Period period,
                                        List<AnalyticsRepository.LabelledAmountRow> rows,
                                        String noun) {
        long total = rows.stream().mapToLong(r -> r.getAmountPaise() == null ? 0L : r.getAmountPaise()).sum();

        List<CategorySlice> slices = rows.stream()
                .map(r -> {
                    long amount = r.getAmountPaise() == null ? 0L : r.getAmountPaise();
                    BigDecimal qty = r.getQuantity() == null
                            ? BigDecimal.ZERO : BigDecimal.valueOf(r.getQuantity());
                    return new CategorySlice(r.getLabel(), amount, rupees(amount), qty, share(amount, total));
                })
                .toList();

        String summary = slices.isEmpty()
                ? "No " + noun + " in this period."
                : slices.size() + " " + noun + " totalling " + rupees(total) + ". Largest: "
                  + slices.get(0).label() + " at " + slices.get(0).amountDisplay()
                  + " (" + slices.get(0).sharePercent() + "%).";

        return new CategoryBreakdown(period, slices, summary);
    }

    @Override
    public CategoryBreakdown salesByCategory(LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");
        return breakdown(period, repository.salesByCategory(tenantId, from, to), "categories");
    }

    @Override
    public CategoryBreakdown paymentMethods(LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");
        return breakdown(period, repository.paymentMethods(tenantId, from, to), "payment methods");
    }

    @Override
    public CategoryBreakdown invoiceStatusSplit(LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");
        return breakdown(period, repository.invoiceStatusSplit(tenantId, from, to), "invoice statuses");
    }

    // -------------------------------------------------------- top products

    @Override
    public ProductPerformanceList topProducts(LocalDate from, LocalDate to, int limit) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");
        int safeLimit = Math.min(Math.max(limit, 1), MAX_TOP_N);

        List<ProductPerformance> products = repository.topProducts(tenantId, from, to, safeLimit)
                .stream()
                .map(r -> {
                    long amount = r.getAmountPaise() == null ? 0L : r.getAmountPaise();
                    long unitPrice = r.getUnitPricePaise() == null ? 0L : r.getUnitPricePaise();
                    return new ProductPerformance(r.getProductId(), r.getLabel(), amount, rupees(amount),
                            r.getQuantity() == null ? BigDecimal.ZERO : r.getQuantity(),
                            unitPrice, rupees(unitPrice));
                })
                .toList();

        String summary = products.isEmpty()
                ? "No products sold in this period."
                : "Top " + products.size() + " products. Best seller: " + products.get(0).label()
                  + " at " + products.get(0).amountDisplay() + ".";

        return new ProductPerformanceList(period, products, summary);
    }

    // ------------------------------------------------- value distribution

    @Override
    public ValueDistribution invoiceValueDistribution(LocalDate from, LocalDate to, int buckets) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");
        int safeBuckets = Math.min(Math.max(buckets, 2), MAX_BUCKETS);

        // A fixed ₹0-₹50,000 window with overflow bins at both ends. Deriving
        // the ceiling from the data instead would make the x-axis move every
        // time a large invoice is raised, so two runs of the same report would
        // not be comparable.
        long floor = 0L;
        long ceiling = 50_000_00L;
        long width = (ceiling - floor) / safeBuckets;

        var rows = repository.invoiceValueDistribution(tenantId, from, to, floor, ceiling, safeBuckets);

        List<ValueBucket> result = new ArrayList<>();
        long total = 0;
        for (int i = 0; i <= safeBuckets + 1; i++) {
            final int index = i;
            long count = rows.stream()
                    .filter(r -> r.getBucket() != null && r.getBucket() == index)
                    .mapToLong(r -> r.getInvoiceCount() == null ? 0L : r.getInvoiceCount())
                    .sum();
            total += count;

            if (i == 0) {
                if (count > 0) {
                    result.add(new ValueBucket("Below " + rupees(floor), Long.MIN_VALUE, floor, count, true));
                }
            } else if (i == safeBuckets + 1) {
                if (count > 0) {
                    result.add(new ValueBucket("Over " + rupees(ceiling), ceiling, Long.MAX_VALUE, count, true));
                }
            } else {
                long lo = floor + (long) (i - 1) * width;
                long hi = lo + width;
                result.add(new ValueBucket(rupees(lo) + " - " + rupees(hi), lo, hi, count, false));
            }
        }

        String summary = total == 0
                ? "No invoices in this period."
                : total + " invoices. " + result.stream()
                        .max((a, b) -> Long.compare(a.invoiceCount(), b.invoiceCount()))
                        .map(b -> "Most fall in " + b.label() + " (" + b.invoiceCount() + ").")
                        .orElse("");

        return new ValueDistribution(period, result, summary);
    }

    // ------------------------------------------------------ sales activity

    @Override
    public ActivityMatrix salesActivity(LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Period period = validate(from, to, "day");

        List<ActivityCell> cells = repository.salesActivity(tenantId, from, to).stream()
                .map(r -> new ActivityCell(
                        r.getDayOfWeek() == null ? 0 : r.getDayOfWeek(),
                        r.getHourOfDay() == null ? 0 : r.getHourOfDay(),
                        r.getInvoiceCount() == null ? 0L : r.getInvoiceCount(),
                        r.getRevenuePaise() == null ? 0L : r.getRevenuePaise(),
                        rupees(r.getRevenuePaise() == null ? 0L : r.getRevenuePaise())))
                .toList();

        long peak = cells.stream().mapToLong(ActivityCell::invoiceCount).max().orElse(0L);

        String summary;
        if (cells.isEmpty()) {
            summary = "No sales activity in this period.";
        } else {
            ActivityCell busiest = cells.stream()
                    .max((a, b) -> Long.compare(a.invoiceCount(), b.invoiceCount())).orElse(cells.get(0));
            String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            summary = "Busiest hour is " + days[busiest.dayOfWeek() % 7] + " at "
                      + String.format("%02d:00", busiest.hourOfDay())
                      + " with " + busiest.invoiceCount() + " invoices.";
        }

        return new ActivityMatrix(period, cells, peak, summary);
    }
}
