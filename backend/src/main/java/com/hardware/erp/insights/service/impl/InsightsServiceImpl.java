package com.hardware.erp.insights.service.impl;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.insights.dto.InsightsDtos.*;
import com.hardware.erp.insights.repository.InsightsRepository;
import com.hardware.erp.insights.repository.InsightsRepository.DemandRow;
import com.hardware.erp.insights.repository.InsightsRepository.PairRow;
import com.hardware.erp.insights.repository.InsightsRepository.ProductSalesRow;
import com.hardware.erp.insights.repository.InsightsRepository.RealisedRow;
import com.hardware.erp.insights.service.InsightsService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plain arithmetic over the repository rows. Where a rate would divide by
 * zero (nothing sold), the figure is null and the row says so, rather than
 * a made-up "infinite" or a hidden zero.
 */
@Service
@RequiredArgsConstructor
public class InsightsServiceImpl implements InsightsService {

    private static final int MAX_ROWS = 50;
    private static final BigDecimal LOW_MARGIN_THRESHOLD = new BigDecimal("10.00");

    private final InsightsRepository repository;
    private final FeatureAccessService featureAccessService;

    @Override
    @Transactional(readOnly = true)
    public SlowMovingResponse slowMoving(int days) {
        Long tenantId = gate();
        Window w = window(days);
        List<SlowMovingItem> items = repository.productSales(tenantId, w.from(), w.to()).stream()
                .filter(r -> r.getQuantityOnHand().signum() > 0 && r.getQuantitySold().signum() == 0)
                .sorted(Comparator.comparing((ProductSalesRow r) -> stockValue(r)).reversed())
                .limit(MAX_ROWS)
                .map(r -> new SlowMovingItem(r.getProductId(), r.getProductCode(), r.getProductName(), r.getUnit(),
                        r.getQuantityOnHand(), r.getQuantitySold(), r.getLastSoldOn(),
                        stockValue(r), IndianCurrencyFormat.rupees(stockValue(r))))
                .toList();
        long value = items.stream().mapToLong(SlowMovingItem::stockValuePaise).sum();
        String summary = items.isEmpty()
                ? "Every product in stock sold at least once in the last " + days + " days."
                : items.size() + " product(s) in stock did not sell in the last " + days + " days, worth "
                + IndianCurrencyFormat.rupees(value) + " at cost.";
        return new SlowMovingResponse(w, items, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public OverstockResponse overstock(int days, int coverThresholdDays) {
        Long tenantId = gate();
        Window w = window(days);
        List<OverstockItem> items = repository.productSales(tenantId, w.from(), w.to()).stream()
                .filter(r -> r.getQuantityOnHand().signum() > 0 && r.getQuantitySold().signum() > 0)
                .map(r -> {
                    BigDecimal daily = dailyRate(r.getQuantitySold(), days);
                    BigDecimal cover = daysOfCover(r.getQuantityOnHand(), daily);
                    return new OverstockItem(r.getProductId(), r.getProductCode(), r.getProductName(), r.getUnit(),
                            r.getQuantityOnHand(), daily, cover, stockValue(r), IndianCurrencyFormat.rupees(stockValue(r)));
                })
                .filter(i -> i.daysOfCover() != null && i.daysOfCover().compareTo(BigDecimal.valueOf(coverThresholdDays)) > 0)
                .sorted(Comparator.comparing(OverstockItem::daysOfCover).reversed())
                .limit(MAX_ROWS)
                .toList();
        String summary = items.isEmpty()
                ? "No product holds more than " + coverThresholdDays + " days of stock at its current rate of sale."
                : items.size() + " product(s) hold more than " + coverThresholdDays + " days of stock at the rate they sold over the last " + days + " days.";
        return new OverstockResponse(w, coverThresholdDays, items, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public ReorderResponse reorder(int days, int leadTimeDays) {
        Long tenantId = gate();
        Window w = window(days);
        List<ReorderSuggestion> items = repository.productSales(tenantId, w.from(), w.to()).stream()
                .map(r -> {
                    BigDecimal daily = dailyRate(r.getQuantitySold(), days);
                    BigDecimal cover = daysOfCover(r.getQuantityOnHand(), daily);
                    BigDecimal reorderLevel = r.getReorderLevel() == null ? BigDecimal.ZERO : r.getReorderLevel();
                    boolean belowLevel = reorderLevel.signum() > 0 && r.getQuantityOnHand().compareTo(reorderLevel) <= 0;
                    boolean runsOutInLead = cover != null && cover.compareTo(BigDecimal.valueOf(leadTimeDays)) <= 0;
                    if (!belowLevel && !runsOutInLead) {
                        return null;
                    }
                    BigDecimal need = daily.multiply(BigDecimal.valueOf(leadTimeDays)).add(reorderLevel)
                            .subtract(r.getQuantityOnHand()).setScale(0, RoundingMode.CEILING);
                    if (need.signum() <= 0) {
                        need = reorderLevel.signum() > 0 ? reorderLevel : BigDecimal.ONE;
                    }
                    String reason = belowLevel
                            ? "At or below its reorder level of " + plain(reorderLevel)
                            : "Sells " + plain(daily) + "/day; " + plain(cover) + " days of stock left, lead time " + leadTimeDays + " days";
                    return new ReorderSuggestion(r.getProductId(), r.getProductCode(), r.getProductName(), r.getUnit(),
                            r.getQuantityOnHand(), reorderLevel, daily, cover, need, reason);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing((ReorderSuggestion s) -> s.daysOfCover() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : s.daysOfCover()))
                .limit(MAX_ROWS)
                .toList();
        String summary = items.isEmpty()
                ? "Nothing needs reordering: no product is at its reorder level or due to run out within " + leadTimeDays + " days."
                : items.size() + " product(s) need reordering - at their reorder level, or due to run out within " + leadTimeDays + " days at the current rate.";
        return new ReorderResponse(w, leadTimeDays, items, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public DemandTrendResponse demandTrend(int days) {
        Long tenantId = gate();
        Window current = window(days);
        Window previous = new Window(current.from().minusDays(days), current.from().minusDays(1), days);
        List<DemandTrendItem> all = repository.demand(tenantId, current.from(), current.to(), previous.from(), previous.to()).stream()
                .map(r -> new DemandTrendItem(r.getProductId(), r.getProductCode(), r.getProductName(), r.getUnit(),
                        r.getCurrentQuantity(), r.getPreviousQuantity(), changePercent(r)))
                .toList();
        List<DemandTrendItem> rising = all.stream()
                .filter(i -> i.currentQuantity().compareTo(i.previousQuantity()) > 0)
                .sorted(Comparator.comparing((DemandTrendItem i) -> i.currentQuantity().subtract(i.previousQuantity())).reversed())
                .limit(MAX_ROWS).toList();
        List<DemandTrendItem> falling = all.stream()
                .filter(i -> i.currentQuantity().compareTo(i.previousQuantity()) < 0)
                .sorted(Comparator.comparing((DemandTrendItem i) -> i.previousQuantity().subtract(i.currentQuantity())).reversed())
                .limit(MAX_ROWS).toList();
        String summary = all.isEmpty()
                ? "Nothing sold in the last " + (2 * days) + " days, so there is no trend to show yet."
                : rising.size() + " product(s) sold more than in the previous " + days + " days, " + falling.size() + " sold less.";
        return new DemandTrendResponse(current, previous, rising, falling, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public BoughtTogetherResponse boughtTogether(int days) {
        Long tenantId = gate();
        Window w = window(days);
        List<BoughtTogetherPair> pairs = repository.boughtTogether(tenantId, w.from(), w.to(), 2, MAX_ROWS).stream()
                .map(r -> new BoughtTogetherPair(r.getProductAId(), r.getProductAName(), r.getProductBId(), r.getProductBName(),
                        r.getInvoicesTogether(), r.getInvoicesWithA(), percent(r.getInvoicesTogether(), r.getInvoicesWithA())))
                .toList();
        String summary = pairs.isEmpty()
                ? "No two products appeared together on at least two invoices in the last " + days + " days."
                : pairs.size() + " product pair(s) were bought together on two or more invoices in the last " + days + " days.";
        return new BoughtTogetherResponse(w, pairs, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public PricingInsightResponse pricing(int days) {
        Long tenantId = gate();
        Window w = window(days);
        Map<Long, RealisedRow> realised = repository.realised(tenantId, w.from(), w.to()).stream()
                .collect(Collectors.toMap(RealisedRow::getProductId, Function.identity()));
        List<PricingInsightItem> items = repository.productSales(tenantId, w.from(), w.to()).stream()
                .filter(r -> r.getSellingPricePaise() != null && r.getSellingPricePaise() > 0)
                .map(r -> {
                    long selling = r.getSellingPricePaise();
                    long cost = r.getAverageCostPaise() == null ? 0L : r.getAverageCostPaise();
                    BigDecimal margin = BigDecimal.valueOf(selling - cost).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(selling), 2, RoundingMode.HALF_UP);
                    RealisedRow rr = realised.get(r.getProductId());
                    long avgRealised = rr == null || rr.getQuantity().signum() == 0 ? 0L
                            : BigDecimal.valueOf(rr.getSubtotalPaise()).divide(rr.getQuantity(), 0, RoundingMode.HALF_UP).longValueExact();
                    String flag = cost > 0 && selling <= cost ? "SELLING_BELOW_COST"
                            : cost > 0 && margin.compareTo(LOW_MARGIN_THRESHOLD) < 0 ? "LOW_MARGIN"
                            : avgRealised > 0 && avgRealised < selling * 0.9 ? "HEAVILY_DISCOUNTED"
                            : null;
                    return flag == null ? null : new PricingInsightItem(r.getProductId(), r.getProductCode(), r.getProductName(),
                            selling, IndianCurrencyFormat.rupees(selling), cost, IndianCurrencyFormat.rupees(cost), margin,
                            avgRealised, IndianCurrencyFormat.rupees(avgRealised), flag);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(PricingInsightItem::marginPercent))
                .limit(MAX_ROWS)
                .toList();
        String summary = items.isEmpty()
                ? "Every product's list price is at least " + LOW_MARGIN_THRESHOLD.stripTrailingZeros().toPlainString()
                + "% above its cost and none sold at a heavy discount in the last " + days + " days."
                : items.size() + " product(s) are priced below cost, under " + LOW_MARGIN_THRESHOLD.stripTrailingZeros().toPlainString()
                + "% margin, or sold at 10%+ below list in the last " + days + " days.";
        return new PricingInsightResponse(w, LOW_MARGIN_THRESHOLD, items, summary);
    }

    private Long gate() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.SMART_INSIGHTS);
        return tenantId;
    }

    private static Window window(int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        return new Window(to.minusDays(d - 1L), to, d);
    }

    private static long stockValue(ProductSalesRow r) {
        long cost = r.getAverageCostPaise() == null ? 0L : r.getAverageCostPaise();
        return r.getQuantityOnHand().multiply(BigDecimal.valueOf(cost)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal dailyRate(BigDecimal sold, int days) {
        return sold.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal daysOfCover(BigDecimal onHand, BigDecimal daily) {
        if (daily == null || daily.signum() <= 0) {
            return null;
        }
        return onHand.divide(daily, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal changePercent(DemandRow r) {
        if (r.getPreviousQuantity() == null || r.getPreviousQuantity().signum() == 0) {
            return null;
        }
        return r.getCurrentQuantity().subtract(r.getPreviousQuantity()).multiply(BigDecimal.valueOf(100))
                .divide(r.getPreviousQuantity(), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
