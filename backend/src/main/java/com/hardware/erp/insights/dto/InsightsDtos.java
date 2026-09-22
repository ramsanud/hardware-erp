package com.hardware.erp.insights.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CR-092 Smart Insights. Every figure is a count, sum or ratio over rows
 * the shop actually recorded (invoice lines, stock, purchases). A window
 * with nothing in it yields an empty list and a summary that says so -
 * never a placeholder number (CLAUDE.md rule 12).
 */
public final class InsightsDtos {

    private InsightsDtos() {
    }

    @Schema(name = "InsightWindow")
    public record Window(LocalDate from, LocalDate to, int days) {}

    @Schema(name = "SlowMovingItem")
    public record SlowMovingItem(Long productId, String productCode, String productName, String unit,
                                 BigDecimal quantityOnHand, BigDecimal quantitySold, LocalDate lastSoldOn,
                                 long stockValuePaise, String stockValueDisplay) {}

    @Schema(name = "SlowMovingResponse")
    public record SlowMovingResponse(Window window, List<SlowMovingItem> items, String summary) {}

    @Schema(name = "OverstockItem")
    public record OverstockItem(Long productId, String productCode, String productName, String unit,
                                BigDecimal quantityOnHand, BigDecimal averageDailySales,
                                @Schema(description = "quantityOnHand / averageDailySales; null when nothing sold") BigDecimal daysOfCover,
                                long stockValuePaise, String stockValueDisplay) {}

    @Schema(name = "OverstockResponse")
    public record OverstockResponse(Window window, int coverThresholdDays, List<OverstockItem> items, String summary) {}

    @Schema(name = "ReorderSuggestion")
    public record ReorderSuggestion(Long productId, String productCode, String productName, String unit,
                                    BigDecimal quantityOnHand, BigDecimal reorderLevel, BigDecimal averageDailySales,
                                    @Schema(description = "Days until stock-out at the current rate; null when nothing sold") BigDecimal daysOfCover,
                                    @Schema(description = "Enough for leadTimeDays of sales plus the reorder level, less what is on hand") BigDecimal suggestedQuantity,
                                    String reason) {}

    @Schema(name = "ReorderResponse")
    public record ReorderResponse(Window window, int leadTimeDays, List<ReorderSuggestion> items, String summary) {}

    @Schema(name = "DemandTrendItem")
    public record DemandTrendItem(Long productId, String productCode, String productName, String unit,
                                  BigDecimal currentQuantity, BigDecimal previousQuantity,
                                  @Schema(description = "Percent change; null when the previous window sold nothing") BigDecimal changePercent) {}

    @Schema(name = "DemandTrendResponse")
    public record DemandTrendResponse(Window current, Window previous, List<DemandTrendItem> rising,
                                      List<DemandTrendItem> falling, String summary) {}

    @Schema(name = "BoughtTogetherPair")
    public record BoughtTogetherPair(Long productAId, String productAName, Long productBId, String productBName,
                                     long invoicesTogether, long invoicesWithA,
                                     @Schema(description = "invoicesTogether / invoicesWithA, as a percent") BigDecimal supportPercent) {}

    @Schema(name = "BoughtTogetherResponse")
    public record BoughtTogetherResponse(Window window, List<BoughtTogetherPair> pairs, String summary) {}

    @Schema(name = "PricingInsightItem")
    public record PricingInsightItem(Long productId, String productCode, String productName,
                                     long sellingPricePaise, String sellingPriceDisplay,
                                     long averageCostPaise, String averageCostDisplay,
                                     @Schema(description = "(selling − cost) / selling, percent") BigDecimal marginPercent,
                                     @Schema(description = "Average price actually charged on invoice lines in the window, after discounts") long averageRealisedPaise,
                                     String averageRealisedDisplay,
                                     String flag) {}

    @Schema(name = "PricingInsightResponse")
    public record PricingInsightResponse(Window window, BigDecimal lowMarginThresholdPercent,
                                         List<PricingInsightItem> items, String summary) {}
}
