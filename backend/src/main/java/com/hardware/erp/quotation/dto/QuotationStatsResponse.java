package com.hardware.erp.quotation.dto;

/**
 * CR-083. The four KPI cards above the quotations list, computed over every
 * quotation that matches the search and date range - never just the current
 * page. Money is a display string like every other quotation DTO.
 *
 * <p>The buckets follow the badge, not the stored column: a DRAFT past its
 * validUntil is "expired" here as it is on screen (CR-022).
 * <ul>
 *   <li>pending  = DRAFT + SENT, still valid</li>
 *   <li>approved = ACCEPTED still valid + CONVERTED</li>
 *   <li>closed   = expired DRAFT/SENT/ACCEPTED + REJECTED</li>
 * </ul>
 * The three always add up to {@code totalCount}.
 */
public record QuotationStatsResponse(
        long totalCount,
        String totalValueDisplay,
        long pendingCount,
        String pendingValueDisplay,
        long approvedCount,
        String approvedValueDisplay,
        long closedCount,
        String closedValueDisplay
) {}
