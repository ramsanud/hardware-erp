package com.hardware.erp.customer.dto;

/**
 * Computed from real invoice/payment rows every time (CR-023) - never
 * cached or fabricated. Cancelled invoices are excluded (see
 * InvoiceRepository.customerFinancialSummary). Return/damage tracking is
 * deliberately absent - no such concept exists anywhere in the schema; see
 * CR-023 for why that was not fabricated here.
 */
public record CustomerFinancialSummaryResponse(
        long invoiceCount,
        long quotationCount,
        String totalInvoicedDisplay,
        String totalPaidDisplay,
        String outstandingBalanceDisplay
) {}
