package com.hardware.erp.report.export;

import com.hardware.erp.report.dto.ReportDtos.*;
import com.hardware.erp.report.export.ReportDocument.Column;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.hardware.erp.report.export.ReportDocument.Column.money;
import static com.hardware.erp.report.export.ReportDocument.Column.text;

/**
 * CR-086. Flattens each report record into a {@link ReportDocument} using
 * the same display strings the JSON carries - the file is the screen.
 */
public final class ReportDocuments {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private ReportDocuments() {
    }

    private static String d(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    private static String period(Period p) {
        return "Period: " + d(p.from()) + " to " + d(p.to());
    }

    public static ReportDocument dayBook(DayBookReport r, String shopName) {
        List<List<String>> rows = new ArrayList<>();
        for (DayBookEntry e : r.entries()) {
            rows.add(List.of(d(e.date()), label(e.kind()), n(e.reference()), n(e.party()), n(e.detail()), e.amountDisplay()));
        }
        DayBookTotals t = r.totals();
        return ReportDocument.builder("Day Book")
                .caption(shopName)
                .caption(period(r.period()))
                .caption("Sales " + t.salesDisplay() + " | Receipts " + t.receiptsDisplay()
                        + " | Credit notes " + t.creditNotesDisplay() + " | Purchases " + t.purchasesDisplay()
                        + " | Expenses " + t.expensesDisplay() + " | Net cash (receipts - expenses) " + t.netCashDisplay())
                .table(null,
                        List.of(text("Date"), text("Type"), text("Reference"), text("Party"), text("Detail"), money("Amount")),
                        rows, null)
                .build();
    }

    public static ReportDocument receivablesAgeing(ReceivablesAgeingReport r, String shopName) {
        List<List<String>> rows = new ArrayList<>();
        for (AgeingRow a : r.rows()) {
            rows.add(List.of(n(a.customerName()), n(a.mobileNo()), String.valueOf(a.openInvoices()),
                    a.current0To30Display(), a.days31To60Display(), a.days61To90Display(), a.over90Display(), a.totalDisplay()));
        }
        AgeingTotals t = r.totals();
        return ReportDocument.builder("Receivables Ageing")
                .caption(shopName)
                .caption("As of " + d(r.asOf()) + " - age counted from the invoice date")
                .table(null,
                        List.of(text("Customer"), text("Mobile"), text("Open bills"), money("0-30 days"),
                                money("31-60 days"), money("61-90 days"), money("Over 90 days"), money("Total due")),
                        rows,
                        List.of("Total", "", "", t.current0To30Display(), t.days31To60Display(),
                                t.days61To90Display(), t.over90Display(), t.totalDisplay()))
                .build();
    }

    public static ReportDocument stockValuation(StockValuationReport r, String shopName) {
        List<List<String>> rows = new ArrayList<>();
        for (StockValuationRow s : r.rows()) {
            rows.add(List.of(n(s.productCode()), n(s.productName()), n(s.categoryName()), n(s.unit()),
                    s.quantityOnHand().toPlainString(), s.purchasePriceDisplay(), s.costValueDisplay(),
                    s.sellingPriceDisplay(), s.sellingValueDisplay()));
        }
        StockValuationTotals t = r.totals();
        return ReportDocument.builder("Stock Valuation")
                .caption(shopName)
                .caption("As of " + d(r.asOf()) + " - stock on hand at today's purchase price; " + t.products() + " products")
                .table(null,
                        List.of(text("Code"), text("Product"), text("Category"), text("Unit"), text("Qty on hand"),
                                money("Purchase rate"), money("Value at cost"), money("Selling rate"), money("Value at selling")),
                        rows,
                        List.of("Total", "", "", "", "", "", t.costValueDisplay(), "", t.sellingValueDisplay()))
                .build();
    }

    public static ReportDocument purchaseRegister(PurchaseRegisterReport r, String shopName) {
        List<List<String>> rows = new ArrayList<>();
        for (PurchaseRegisterRow p : r.rows()) {
            rows.add(List.of(d(p.purchaseDate()), n(p.purchaseNumber()), n(p.supplierBillNumber()), n(p.supplierName()),
                    n(p.supplierGstNo()), p.taxableDisplay(), p.cgstDisplay(), p.sgstDisplay(), p.igstDisplay(),
                    p.totalDisplay(), p.paidDisplay(), p.balanceDisplay()));
        }
        PurchaseRegisterTotals t = r.totals();
        return ReportDocument.builder("Purchase Register")
                .caption(shopName)
                .caption(period(r.period()) + " - " + t.bills() + " bills")
                .table(null,
                        List.of(text("Date"), text("Purchase no."), text("Supplier bill"), text("Supplier"), text("GSTIN"),
                                money("Taxable"), money("CGST"), money("SGST"), money("IGST"), money("Total"),
                                money("Paid"), money("Balance")),
                        rows,
                        List.of("Total", "", "", "", "", t.taxableDisplay(), t.cgstDisplay(), t.sgstDisplay(),
                                t.igstDisplay(), t.totalDisplay(), t.paidDisplay(), t.balanceDisplay()))
                .build();
    }

    public static ReportDocument gstSummary(GstSummaryReport r, String shopName) {
        ReportDocument.Builder b = ReportDocument.builder("GST Summary")
                .caption(shopName)
                .caption(period(r.period()))
                .caption("Net payable (output - credit notes - input): CGST " + r.netCgstDisplay()
                        + " | SGST " + r.netSgstDisplay() + " | IGST " + r.netIgstDisplay() + " | Total " + r.netTaxDisplay())
                .caption("Input credit is shown as booked; whether it is claimable depends on the supplier having filed.");
        for (GstSection section : List.of(r.outward(), r.creditNotes(), r.inward())) {
            List<List<String>> rows = new ArrayList<>();
            for (GstRateRow row : section.rows()) {
                rows.add(List.of(row.ratePercent().toPlainString() + "%", row.taxableDisplay(), row.cgstDisplay(),
                        row.sgstDisplay(), row.igstDisplay(), row.totalTaxDisplay()));
            }
            GstRateRow t = section.totals();
            b.table(section.title(),
                    List.of(text("Rate"), money("Taxable value"), money("CGST"), money("SGST"), money("IGST"), money("Total tax")),
                    rows,
                    List.of("Total", t.taxableDisplay(), t.cgstDisplay(), t.sgstDisplay(), t.igstDisplay(), t.totalTaxDisplay()));
        }
        return b.build();
    }

    private static String label(DayBookKind kind) {
        return switch (kind) {
            case SALE -> "Sale";
            case RECEIPT -> "Receipt";
            case CREDIT_NOTE -> "Credit note";
            case PURCHASE -> "Purchase";
            case EXPENSE -> "Expense";
        };
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
