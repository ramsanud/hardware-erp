package com.hardware.erp.dashboard.dto;

/**
 * Dashboard sales-side metrics (CR-023) - computed from real invoice rows
 * every call, never cached or fabricated. Purchase-side metrics
 * (Total/Today's Purchases, Outstanding Supplier Balance) are deliberately
 * absent - no Purchase module exists yet, see CR-023.
 *
 * todaySalesPaise/yesterdaySalesPaise (CR-033) are raw paise, not display
 * strings - the frontend computes the trend percentage itself (a
 * dashboard KPI card's "+12.8% vs yesterday", real, never fabricated -
 * see PROJECT_SKILLS on that ethic) rather than duplicating that
 * arithmetic server-side just to also format it there.
 */
public record SalesSummaryResponse(
        String totalSalesDisplay,
        String todaySalesDisplay,
        String outstandingCustomerBalanceDisplay,
        long todaySalesPaise,
        long yesterdaySalesPaise
) {}
