package com.hardware.erp.report.service.impl;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.report.dto.ReportDtos.*;
import com.hardware.erp.report.repository.ReportRepository;
import com.hardware.erp.report.repository.ReportRepository.AgeingRowView;
import com.hardware.erp.report.repository.ReportRepository.PurchaseRowView;
import com.hardware.erp.report.repository.ReportRepository.RateSlabView;
import com.hardware.erp.report.repository.ReportRepository.StockRowView;
import com.hardware.erp.report.repository.ReportRepository.VoucherRow;
import com.hardware.erp.report.service.ReportService;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hardware.erp.common.util.IndianCurrencyFormat.rupees;

/**
 * CR-086. Shapes the repository aggregates into the report records. The
 * only arithmetic here is the CGST/SGST vs IGST split and the totals rows -
 * the sums themselves come from PostgreSQL.
 *
 * The split follows InvoicePdfService exactly (half of the GST rounded
 * down to CGST, the remainder to SGST, so the two always add up to the
 * stored total), because a report that disagrees with the invoice it
 * summarises is worse than no report.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    /** The longest range a single report may cover; anything longer is a data export, not a report. */
    static final int MAX_RANGE_DAYS = 366;
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final ReportRepository reportRepository;

    // ------------------------------------------------------------ Day Book

    @Override
    public DayBookReport dayBook(LocalDate from, LocalDate to) {
        requireRange(from, to);
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        List<DayBookEntry> entries = new ArrayList<>();
        long sales = add(entries, DayBookKind.SALE, reportRepository.dayBookSales(tenantId, from, to));
        long receipts = add(entries, DayBookKind.RECEIPT, reportRepository.dayBookReceipts(tenantId, from, to));
        long creditNotes = add(entries, DayBookKind.CREDIT_NOTE, reportRepository.dayBookCreditNotes(tenantId, from, to));
        long purchases = add(entries, DayBookKind.PURCHASE, reportRepository.dayBookPurchases(tenantId, from, to));
        long expenses = add(entries, DayBookKind.EXPENSE, reportRepository.dayBookExpenses(tenantId, from, to));

        // Chronological, and within a day in voucher order (sale before its receipt).
        entries.sort(Comparator.comparing(DayBookEntry::date).thenComparing(DayBookEntry::kind));

        long netCash = receipts - expenses;
        return new DayBookReport(new Period(from, to), entries, new DayBookTotals(
                sales, rupees(sales),
                receipts, rupees(receipts),
                creditNotes, rupees(creditNotes),
                purchases, rupees(purchases),
                expenses, rupees(expenses),
                netCash, rupees(netCash)));
    }

    private static long add(List<DayBookEntry> into, DayBookKind kind, List<VoucherRow> rows) {
        long total = 0;
        for (VoucherRow row : rows) {
            long amount = row.getAmountPaise() == null ? 0 : row.getAmountPaise();
            total += amount;
            String reference = kind == DayBookKind.EXPENSE ? "EXP-" + row.getReference() : row.getReference();
            into.add(new DayBookEntry(row.getDate(), kind, reference, row.getParty(),
                    humanise(row.getDetail()), amount, rupees(amount)));
        }
        return total;
    }

    /** PARTIALLY_PAID -> "Partially paid"; a free-text reason passes through untouched. */
    private static String humanise(String detail) {
        if (detail == null || detail.isBlank()) return null;
        if (!detail.equals(detail.toUpperCase()) || !detail.matches("[A-Z_]+")) return detail;
        String lower = detail.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // -------------------------------------------------- Receivables Ageing

    @Override
    public ReceivablesAgeingReport receivablesAgeing(LocalDate asOf) {
        LocalDate day = asOf == null ? LocalDate.now(SHOP_ZONE) : asOf;
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        long b0 = 0, b31 = 0, b61 = 0, b91 = 0;
        List<AgeingRow> rows = new ArrayList<>();
        for (AgeingRowView v : reportRepository.receivablesAgeing(tenantId, day)) {
            long c0 = nz(v.getBucket0()), c31 = nz(v.getBucket31()), c61 = nz(v.getBucket61()), c91 = nz(v.getBucket91());
            long total = c0 + c31 + c61 + c91;
            b0 += c0; b31 += c31; b61 += c61; b91 += c91;
            rows.add(new AgeingRow(v.getCustomerId(), v.getCustomerName(), v.getMobileNo(),
                    c0, rupees(c0), c31, rupees(c31), c61, rupees(c61), c91, rupees(c91),
                    total, rupees(total), (int) nz(v.getOpenInvoices())));
        }
        long grand = b0 + b31 + b61 + b91;
        return new ReceivablesAgeingReport(day, rows, new AgeingTotals(
                b0, rupees(b0), b31, rupees(b31), b61, rupees(b61), b91, rupees(b91), grand, rupees(grand)));
    }

    // ------------------------------------------------------ Stock Valuation

    @Override
    public StockValuationReport stockValuation() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        long cost = 0, selling = 0;
        List<StockValuationRow> rows = new ArrayList<>();
        for (StockRowView v : reportRepository.stockValuation(tenantId)) {
            BigDecimal qty = v.getQuantityOnHand() == null ? BigDecimal.ZERO : v.getQuantityOnHand();
            long purchase = nz(v.getPurchasePricePaise());
            long sell = nz(v.getSellingPricePaise());
            // Quantity is DECIMAL(18,4); the value is rounded to a whole paisa once, here.
            long costValue = qty.multiply(BigDecimal.valueOf(purchase)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            long sellValue = qty.multiply(BigDecimal.valueOf(sell)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            cost += costValue;
            selling += sellValue;
            rows.add(new StockValuationRow(v.getProductId(), v.getProductCode(), v.getProductName(),
                    v.getCategoryName(), v.getUnit(), qty.stripTrailingZeros(),
                    purchase, rupees(purchase), sell, rupees(sell),
                    costValue, rupees(costValue), sellValue, rupees(sellValue)));
        }
        return new StockValuationReport(LocalDate.now(SHOP_ZONE), rows,
                new StockValuationTotals(rows.size(), cost, rupees(cost), selling, rupees(selling)));
    }

    // ---------------------------------------------------- Purchase Register

    @Override
    public PurchaseRegisterReport purchaseRegister(LocalDate from, LocalDate to) {
        requireRange(from, to);
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        long taxable = 0, cgst = 0, sgst = 0, igst = 0, total = 0, paid = 0, balance = 0;
        List<PurchaseRegisterRow> rows = new ArrayList<>();
        for (PurchaseRowView v : reportRepository.purchaseRegister(tenantId, from, to)) {
            long[] split = split(nz(v.getGstAmountPaise()), Boolean.TRUE.equals(v.getInterState()));
            long t = nz(v.getSubtotalPaise());
            taxable += t; cgst += split[0]; sgst += split[1]; igst += split[2];
            total += nz(v.getTotalPaise()); paid += nz(v.getPaidPaise()); balance += nz(v.getBalancePaise());
            rows.add(new PurchaseRegisterRow(v.getPurchaseId(), v.getPurchaseDate(), v.getPurchaseNumber(),
                    v.getSupplierBillNumber(), v.getSupplierName(), v.getSupplierGstNo(), v.getStatus(),
                    t, rupees(t),
                    split[0], rupees(split[0]), split[1], rupees(split[1]), split[2], rupees(split[2]),
                    nz(v.getTotalPaise()), rupees(nz(v.getTotalPaise())),
                    nz(v.getPaidPaise()), rupees(nz(v.getPaidPaise())),
                    nz(v.getBalancePaise()), rupees(nz(v.getBalancePaise()))));
        }
        return new PurchaseRegisterReport(new Period(from, to), rows, new PurchaseRegisterTotals(
                rows.size(), taxable, rupees(taxable), cgst, rupees(cgst), sgst, rupees(sgst), igst, rupees(igst),
                total, rupees(total), paid, rupees(paid), balance, rupees(balance)));
    }

    // ---------------------------------------------------------- GST Summary

    @Override
    public GstSummaryReport gstSummary(LocalDate from, LocalDate to) {
        requireRange(from, to);
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        GstSection outward = section("Outward supplies (sales)", reportRepository.outwardByRate(tenantId, from, to));
        GstSection creditNotes = section("Credit notes issued", reportRepository.creditNotesByRate(tenantId, from, to));
        GstSection inward = section("Inward supplies (purchases)", reportRepository.inwardByRate(tenantId, from, to));

        long netCgst = outward.totals().cgstPaise() - creditNotes.totals().cgstPaise() - inward.totals().cgstPaise();
        long netSgst = outward.totals().sgstPaise() - creditNotes.totals().sgstPaise() - inward.totals().sgstPaise();
        long netIgst = outward.totals().igstPaise() - creditNotes.totals().igstPaise() - inward.totals().igstPaise();
        long net = netCgst + netSgst + netIgst;
        return new GstSummaryReport(new Period(from, to), outward, creditNotes, inward,
                netCgst, rupees(netCgst), netSgst, rupees(netSgst), netIgst, rupees(netIgst), net, rupees(net));
    }

    /** Folds the (rate, interState) slabs into one row per rate with CGST/SGST/IGST side by side. */
    static GstSection section(String title, List<RateSlabView> slabs) {
        Map<BigDecimal, long[]> byRate = new TreeMap<>();
        for (RateSlabView slab : slabs) {
            BigDecimal rate = slab.getRatePercent() == null ? BigDecimal.ZERO : slab.getRatePercent().stripTrailingZeros();
            long[] acc = byRate.computeIfAbsent(rate, r -> new long[4]);
            long[] split = split(nz(slab.getGstPaise()), Boolean.TRUE.equals(slab.getInterState()));
            acc[0] += nz(slab.getTaxablePaise());
            acc[1] += split[0];
            acc[2] += split[1];
            acc[3] += split[2];
        }
        long[] sum = new long[4];
        List<GstRateRow> rows = new ArrayList<>();
        for (Map.Entry<BigDecimal, long[]> e : byRate.entrySet()) {
            long[] a = e.getValue();
            for (int i = 0; i < 4; i++) sum[i] += a[i];
            rows.add(row(e.getKey(), a));
        }
        return new GstSection(title, rows, row(null, sum));
    }

    private static GstRateRow row(BigDecimal rate, long[] a) {
        long tax = a[1] + a[2] + a[3];
        return new GstRateRow(rate, a[0], rupees(a[0]), a[1], rupees(a[1]), a[2], rupees(a[2]), a[3], rupees(a[3]),
                tax, rupees(tax));
    }

    /** {cgst, sgst, igst} - the InvoicePdfService split, so the three always sum to {@code gst}. */
    static long[] split(long gst, boolean interState) {
        if (interState) return new long[] {0, 0, gst};
        long cgst = gst / 2;
        return new long[] {cgst, gst - cgst, 0};
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("Both from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("The start date must be on or before the end date");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessException("A report can cover at most one year at a time");
        }
    }
}
