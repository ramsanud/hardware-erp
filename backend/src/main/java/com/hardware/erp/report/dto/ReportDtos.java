package com.hardware.erp.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Report response shapes (CR-086), grouped in one file for the same reason
 * AnalyticsDtos is: each is a few lines and they are only ever consumed
 * together by ReportController and ReportExporter.
 *
 * Every money field is raw paise plus a display string, so the browser
 * never formats money (Indian digit grouping lives in IndianCurrencyFormat,
 * once). The PDF and Excel exports are built from these exact objects, so a
 * downloaded file can never disagree with the screen it was downloaded from.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record Period(LocalDate from, LocalDate to) {}

    // ------------------------------------------------------------ Day Book

    /** Every voucher kind the day book lists, in the order the totals row shows them. */
    public enum DayBookKind { SALE, RECEIPT, CREDIT_NOTE, PURCHASE, EXPENSE }

    /**
     * One voucher. {@code reference} is the document number; {@code party}
     * the customer/supplier/expense category; {@code detail} the payment
     * method or reason where one exists.
     */
    public record DayBookEntry(
            LocalDate date,
            DayBookKind kind,
            String reference,
            String party,
            String detail,
            long amountPaise,
            String amountDisplay
    ) {}

    public record DayBookTotals(
            long salesPaise, String salesDisplay,
            long receiptsPaise, String receiptsDisplay,
            long creditNotesPaise, String creditNotesDisplay,
            long purchasesPaise, String purchasesDisplay,
            long expensesPaise, String expensesDisplay,
            /** Receipts minus expenses: what actually moved through the till. Purchases are on supplier credit until paid. */
            long netCashPaise, String netCashDisplay
    ) {}

    public record DayBookReport(Period period, List<DayBookEntry> entries, DayBookTotals totals) {}

    // -------------------------------------------------- Receivables Ageing

    /** One customer with a balance still due, split by how old the invoices are on {@code asOf}. */
    public record AgeingRow(
            Long customerId,
            String customerName,
            String mobileNo,
            long current0To30Paise, String current0To30Display,
            long days31To60Paise, String days31To60Display,
            long days61To90Paise, String days61To90Display,
            long over90Paise, String over90Display,
            long totalPaise, String totalDisplay,
            int openInvoices
    ) {}

    public record AgeingTotals(
            long current0To30Paise, String current0To30Display,
            long days31To60Paise, String days31To60Display,
            long days61To90Paise, String days61To90Display,
            long over90Paise, String over90Display,
            long totalPaise, String totalDisplay
    ) {}

    public record ReceivablesAgeingReport(LocalDate asOf, List<AgeingRow> rows, AgeingTotals totals) {}

    // ------------------------------------------------------ Stock Valuation

    /**
     * Valued at the product's current purchase price. There is no cost
     * history table, so "what it cost when it came in" cannot be recovered —
     * this is a snapshot of today's stock at today's purchase rate, and the
     * screen says so.
     */
    public record StockValuationRow(
            Long productId,
            String productCode,
            String productName,
            String categoryName,
            String unit,
            BigDecimal quantityOnHand,
            long purchasePricePaise, String purchasePriceDisplay,
            long sellingPricePaise, String sellingPriceDisplay,
            long costValuePaise, String costValueDisplay,
            long sellingValuePaise, String sellingValueDisplay
    ) {}

    public record StockValuationTotals(
            int products,
            long costValuePaise, String costValueDisplay,
            long sellingValuePaise, String sellingValueDisplay
    ) {}

    public record StockValuationReport(LocalDate asOf, List<StockValuationRow> rows, StockValuationTotals totals) {}

    // ---------------------------------------------------- Purchase Register

    public record PurchaseRegisterRow(
            Long purchaseId,
            LocalDate purchaseDate,
            String purchaseNumber,
            String supplierBillNumber,
            String supplierName,
            String supplierGstNo,
            String status,
            long taxablePaise, String taxableDisplay,
            long cgstPaise, String cgstDisplay,
            long sgstPaise, String sgstDisplay,
            long igstPaise, String igstDisplay,
            long totalPaise, String totalDisplay,
            long paidPaise, String paidDisplay,
            long balancePaise, String balanceDisplay
    ) {}

    public record PurchaseRegisterTotals(
            int bills,
            long taxablePaise, String taxableDisplay,
            long cgstPaise, String cgstDisplay,
            long sgstPaise, String sgstDisplay,
            long igstPaise, String igstDisplay,
            long totalPaise, String totalDisplay,
            long paidPaise, String paidDisplay,
            long balancePaise, String balanceDisplay
    ) {}

    public record PurchaseRegisterReport(Period period, List<PurchaseRegisterRow> rows, PurchaseRegisterTotals totals) {}

    // ---------------------------------------------------------- GST Summary

    /** One GST rate slab. The same shape serves outward supplies, credit notes and inward supplies. */
    public record GstRateRow(
            BigDecimal ratePercent,
            long taxablePaise, String taxableDisplay,
            long cgstPaise, String cgstDisplay,
            long sgstPaise, String sgstDisplay,
            long igstPaise, String igstDisplay,
            long totalTaxPaise, String totalTaxDisplay
    ) {}

    public record GstSection(String title, List<GstRateRow> rows, GstRateRow totals) {}

    /**
     * Net = output tax (sales) minus credit-note tax minus input tax
     * (purchases). Negative means input credit exceeds liability for the
     * period. Whether the input credit is actually claimable depends on the
     * supplier having filed — this app cannot know that, and the screen
     * says so.
     */
    public record GstSummaryReport(
            Period period,
            GstSection outward,
            GstSection creditNotes,
            GstSection inward,
            long netCgstPaise, String netCgstDisplay,
            long netSgstPaise, String netSgstDisplay,
            long netIgstPaise, String netIgstDisplay,
            long netTaxPaise, String netTaxDisplay
    ) {}
}
