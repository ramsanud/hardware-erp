package com.hardware.erp.report.gst;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.util.Gstin;
import com.hardware.erp.creditnote.entity.CreditNote;
import com.hardware.erp.creditnote.entity.CreditNoteItem;
import com.hardware.erp.creditnote.repository.CreditNoteRepository;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * CR-087. Builds the GSTR-1 return for one tax period in the JSON layout the
 * GST portal's offline tool imports: {@code b2b}, {@code b2cl}, {@code b2cs},
 * {@code cdnr}, {@code cdnur} and the {@code hsn} summary.
 *
 * What this is and is not:
 * - Figures come from the same invoice/credit-note rows the invoice PDF
 *   prints; taxable value and tax are the line snapshots, the invoice value
 *   is the stored total. A coupon discount applied at invoice level (CR-047)
 *   lowers the total but not the lines, so {@code val} can be below the sum
 *   of {@code txval + tax} for such an invoice - the portal accepts that.
 * - Place of supply is the customer's state; a customer with no state is
 *   treated as intra-state, the same rule InvoicePdfService uses.
 * - A credit note against a registered buyer goes to {@code cdnr}; against
 *   an unregistered inter-state buyer above the B2CL threshold to
 *   {@code cdnur}; every other unregistered credit note is netted into
 *   {@code b2cs}, which is how the portal expects small-consumer returns.
 * - No {@code nil}, {@code exp}, {@code at} or {@code txpd} sections: this
 *   app has no export, advance or nil-rated flows to report.
 * - Nothing here has been round-tripped through the offline tool in this
 *   environment (none is installed); the shape follows the published
 *   specification and the ITs check it structurally.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Gstr1Service {

    /**
     * Inter-state supplies to unregistered persons above this invoice value are
     * reported invoice-wise (B2CL). Rs 1,00,000 since 1 Aug 2024 (was 2,50,000).
     */
    static final long B2CL_THRESHOLD_PAISE = 100_000L * 100;

    private static final DateTimeFormatter GST_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final TenantRepository tenantRepository;

    /** @param period the return period as {@code MMyyyy}, e.g. {@code 092026}. */
    public Map<String, Object> build(String period) {
        YearMonth month = parsePeriod(period);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Shop not found"));
        if (!Gstin.isValid(tenant.getGstNo())) {
            throw new BusinessException("Set a valid GSTIN in Shop settings before generating GSTR-1");
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<Invoice> invoices = invoiceRepository.findForExport(tenantId, from, to);
        List<CreditNote> notes = creditNoteRepository.findForExport(tenantId, from, to);

        String shopState = tenant.getStateCode();

        Map<String, List<Map<String, Object>>> b2b = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> b2cl = new LinkedHashMap<>();
        Map<String, long[]> b2cs = new TreeMap<>();           // key: pos|rate|sply_ty -> {txval, cgst, sgst, igst}
        Map<String, long[]> hsn = new TreeMap<>();            // key: hsn|rate|uqc -> {qty(x10000), val, txval, cgst, sgst, igst}
        Map<String, String> hsnDesc = new LinkedHashMap<>();

        for (Invoice invoice : invoices) {
            Customer customer = invoice.getCustomer();
            boolean registered = Gstin.isValid(customer.getGstNo());
            String pos = placeOfSupply(customer, shopState);
            boolean interState = !pos.equals(shopState);

            List<Map<String, Object>> items = itemsByRate(invoice.getItems(), interState);
            Map<String, Object> inv = new LinkedHashMap<>();
            inv.put("inum", invoice.getInvoiceNumber());
            inv.put("idt", GST_DATE.format(invoice.getInvoiceDate()));
            inv.put("val", rupees(invoice.getTotalPaise()));
            inv.put("pos", pos);
            if (registered) {
                inv.put("rchrg", "N");
                inv.put("inv_typ", "R");
                inv.put("itms", items);
                b2b.computeIfAbsent(customer.getGstNo(), k -> new ArrayList<>()).add(inv);
            } else if (interState && invoice.getTotalPaise() > B2CL_THRESHOLD_PAISE) {
                inv.put("itms", items);
                b2cl.computeIfAbsent(pos, k -> new ArrayList<>()).add(inv);
            } else {
                accumulateB2cs(b2cs, invoice.getItems(), pos, interState, 1);
            }
            for (InvoiceItem item : invoice.getItems()) {
                accumulateHsn(hsn, hsnDesc, item.getProduct(), item.getProductNameSnapshot(), item.getUnit(),
                        item.getQuantity(), item.getGstRatePercent(), item.getLineTotalPaise(),
                        item.getLineSubtotalPaise(), item.getLineGstPaise(), interState, 1);
            }
        }

        Map<String, List<Map<String, Object>>> cdnr = new LinkedHashMap<>();
        List<Map<String, Object>> cdnur = new ArrayList<>();
        for (CreditNote note : notes) {
            Customer customer = note.getCustomer();
            boolean registered = Gstin.isValid(customer.getGstNo());
            String pos = placeOfSupply(customer, shopState);
            boolean interState = !pos.equals(shopState);
            Invoice against = note.getInvoice();

            Map<String, Object> nt = new LinkedHashMap<>();
            nt.put("ntty", "C");
            nt.put("nt_num", note.getCreditNoteNumber());
            nt.put("nt_dt", GST_DATE.format(note.getCreditNoteDate()));
            nt.put("pos", pos);
            nt.put("rchrg", "N");
            nt.put("p_gst", "N");
            nt.put("val", rupees(note.getTotalPaise()));
            nt.put("itms", noteItemsByRate(note.getItems(), interState));
            if (registered) {
                nt.put("inv_typ", "R");
                cdnr.computeIfAbsent(customer.getGstNo(), k -> new ArrayList<>()).add(nt);
            } else if (interState && against != null && against.getTotalPaise() > B2CL_THRESHOLD_PAISE) {
                nt.put("typ", "B2CL");
                cdnur.add(nt);
            } else {
                accumulateB2csNote(b2cs, note.getItems(), pos, interState);
            }
            for (CreditNoteItem item : note.getItems()) {
                accumulateHsn(hsn, hsnDesc, item.getProduct(), item.getProductNameSnapshot(), item.getUnit(),
                        item.getQuantity(), item.getGstRatePercent(), item.getLineTotalPaise(),
                        item.getLineSubtotalPaise(), item.getLineGstPaise(), interState, -1);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gstin", tenant.getGstNo());
        out.put("fp", period);
        out.put("version", "GST3.1.6");
        out.put("hash", "hash");
        out.put("b2b", ctinList(b2b, "inv"));
        out.put("b2cl", posList(b2cl));
        out.put("b2cs", b2csList(b2cs));
        out.put("cdnr", ctinList(cdnr, "nt"));
        out.put("cdnur", cdnur);
        out.put("hsn", Map.of("data", hsnList(hsn, hsnDesc)));
        return out;
    }

    // -------------------------------------------------------------- sections

    private static List<Map<String, Object>> ctinList(Map<String, List<Map<String, Object>>> byCtin, String key) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byCtin.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ctin", e.getKey());
            entry.put(key, e.getValue());
            list.add(entry);
        }
        return list;
    }

    private static List<Map<String, Object>> posList(Map<String, List<Map<String, Object>>> byPos) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byPos.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pos", e.getKey());
            entry.put("inv", e.getValue());
            list.add(entry);
        }
        return list;
    }

    private static void accumulateB2cs(Map<String, long[]> b2cs, List<InvoiceItem> items, String pos,
                                       boolean interState, int sign) {
        for (InvoiceItem item : items) {
            addB2cs(b2cs, pos, interState, item.getGstRatePercent(), item.getLineSubtotalPaise(), item.getLineGstPaise(), sign);
        }
    }

    private static void accumulateB2csNote(Map<String, long[]> b2cs, List<CreditNoteItem> items, String pos,
                                           boolean interState) {
        for (CreditNoteItem item : items) {
            addB2cs(b2cs, pos, interState, item.getGstRatePercent(), item.getLineSubtotalPaise(), item.getLineGstPaise(), -1);
        }
    }

    private static void addB2cs(Map<String, long[]> b2cs, String pos, boolean interState, BigDecimal rate,
                                Long taxable, Long gst, int sign) {
        String key = pos + "|" + rateKey(rate) + "|" + (interState ? "INTER" : "INTRA");
        long[] acc = b2cs.computeIfAbsent(key, k -> new long[4]);
        long[] split = split(nz(gst), interState);
        acc[0] += sign * nz(taxable);
        acc[1] += sign * split[0];
        acc[2] += sign * split[1];
        acc[3] += sign * split[2];
    }

    private static List<Map<String, Object>> b2csList(Map<String, long[]> b2cs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, long[]> e : b2cs.entrySet()) {
            String[] k = e.getKey().split("\\|");
            long[] a = e.getValue();
            if (a[0] == 0 && a[1] == 0 && a[2] == 0 && a[3] == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sply_ty", k[2]);
            row.put("rt", new BigDecimal(k[1]));
            row.put("typ", "OE");
            row.put("pos", k[0]);
            row.put("txval", rupees(a[0]));
            if ("INTER".equals(k[2])) {
                row.put("iamt", rupees(a[3]));
            } else {
                row.put("camt", rupees(a[1]));
                row.put("samt", rupees(a[2]));
            }
            row.put("csamt", BigDecimal.ZERO.setScale(2));
            list.add(row);
        }
        return list;
    }

    private static List<Map<String, Object>> itemsByRate(List<InvoiceItem> items, boolean interState) {
        Map<String, long[]> byRate = new TreeMap<>();
        for (InvoiceItem item : items) {
            long[] acc = byRate.computeIfAbsent(rateKey(item.getGstRatePercent()), k -> new long[4]);
            long[] split = split(nz(item.getLineGstPaise()), interState);
            acc[0] += nz(item.getLineSubtotalPaise());
            acc[1] += split[0]; acc[2] += split[1]; acc[3] += split[2];
        }
        return itemList(byRate, interState);
    }

    private static List<Map<String, Object>> noteItemsByRate(List<CreditNoteItem> items, boolean interState) {
        Map<String, long[]> byRate = new TreeMap<>();
        for (CreditNoteItem item : items) {
            long[] acc = byRate.computeIfAbsent(rateKey(item.getGstRatePercent()), k -> new long[4]);
            long[] split = split(nz(item.getLineGstPaise()), interState);
            acc[0] += nz(item.getLineSubtotalPaise());
            acc[1] += split[0]; acc[2] += split[1]; acc[3] += split[2];
        }
        return itemList(byRate, interState);
    }

    private static List<Map<String, Object>> itemList(Map<String, long[]> byRate, boolean interState) {
        List<Map<String, Object>> list = new ArrayList<>();
        int num = 1;
        for (Map.Entry<String, long[]> e : byRate.entrySet()) {
            long[] a = e.getValue();
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("rt", new BigDecimal(e.getKey()));
            det.put("txval", rupees(a[0]));
            if (interState) {
                det.put("iamt", rupees(a[3]));
            } else {
                det.put("camt", rupees(a[1]));
                det.put("samt", rupees(a[2]));
            }
            det.put("csamt", BigDecimal.ZERO.setScale(2));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("num", num++);
            item.put("itm_det", det);
            list.add(item);
        }
        return list;
    }

    private static void accumulateHsn(Map<String, long[]> hsn, Map<String, String> desc, Product product,
                                      String nameSnapshot, String unit, BigDecimal quantity, BigDecimal rate,
                                      Long lineTotal, Long taxable, Long gst, boolean interState, int sign) {
        String code = product != null && product.getHsnCode() != null && !product.getHsnCode().isBlank()
                ? product.getHsnCode() : "";
        String uqc = uqc(unit);
        String key = code + "|" + rateKey(rate) + "|" + uqc;
        long[] acc = hsn.computeIfAbsent(key, k -> new long[6]);
        long[] split = split(nz(gst), interState);
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        acc[0] += sign * qty.movePointRight(4).setScale(0, RoundingMode.HALF_UP).longValue();
        acc[1] += sign * nz(lineTotal);
        acc[2] += sign * nz(taxable);
        acc[3] += sign * split[0];
        acc[4] += sign * split[1];
        acc[5] += sign * split[2];
        desc.putIfAbsent(key, product != null ? product.getProductName() : nameSnapshot);
    }

    private static List<Map<String, Object>> hsnList(Map<String, long[]> hsn, Map<String, String> desc) {
        List<Map<String, Object>> list = new ArrayList<>();
        int num = 1;
        for (Map.Entry<String, long[]> e : hsn.entrySet()) {
            String[] k = e.getKey().split("\\|", -1);
            long[] a = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("num", num++);
            row.put("hsn_sc", k[0]);
            row.put("desc", desc.get(e.getKey()));
            row.put("uqc", k[2]);
            row.put("qty", BigDecimal.valueOf(a[0]).movePointLeft(4).setScale(2, RoundingMode.HALF_UP));
            row.put("rt", new BigDecimal(k[1]));
            row.put("val", rupees(a[1]));
            row.put("txval", rupees(a[2]));
            row.put("camt", rupees(a[3]));
            row.put("samt", rupees(a[4]));
            row.put("iamt", rupees(a[5]));
            row.put("csamt", BigDecimal.ZERO.setScale(2));
            list.add(row);
        }
        return list;
    }

    // --------------------------------------------------------------- helpers

    static YearMonth parsePeriod(String period) {
        if (period == null || !period.matches("(0[1-9]|1[0-2])[0-9]{4}")) {
            throw new BusinessException("period must be MMYYYY, e.g. 092026");
        }
        return YearMonth.of(Integer.parseInt(period.substring(2)), Integer.parseInt(period.substring(0, 2)));
    }

    private static String placeOfSupply(Customer customer, String shopState) {
        String state = customer.getStateCode();
        if (state == null || state.isBlank()) {
            // Registered buyers carry their state in the GSTIN's first two characters.
            if (Gstin.isValid(customer.getGstNo())) return customer.getGstNo().substring(0, 2);
            return shopState == null ? "" : shopState;
        }
        return state;
    }

    /** {cgst, sgst, igst} - the InvoicePdfService split. */
    static long[] split(long gst, boolean interState) {
        if (interState) return new long[] {0, 0, gst};
        long cgst = gst / 2;
        return new long[] {cgst, gst - cgst, 0};
    }

    private static String rateKey(BigDecimal rate) {
        return (rate == null ? BigDecimal.ZERO : rate).stripTrailingZeros().toPlainString();
    }

    static BigDecimal rupees(Long paise) {
        return BigDecimal.valueOf(nz(paise)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    /**
     * Unit Quantity Code. Units in this app are free text the owner typed
     * ("pcs", "kg", "mtr"); the portal wants its own eight-letter codes.
     * Anything unrecognised is OTH, which the portal accepts.
     */
    static String uqc(String unit) {
        if (unit == null) return "OTH-OTHERS";
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "pcs", "pc", "piece", "pieces", "nos", "no", "number", "numbers", "unit", "units" -> "NOS-NUMBERS";
            case "kg", "kgs", "kilogram", "kilograms" -> "KGS-KILOGRAMS";
            case "g", "gm", "gms", "gram", "grams" -> "GMS-GRAMMES";
            case "m", "mtr", "mtrs", "meter", "meters", "metre", "metres" -> "MTR-METERS";
            case "cm", "cms" -> "CMS-CENTIMETERS";
            case "ft", "feet", "foot" -> "FTS-FEET";
            case "l", "ltr", "ltrs", "litre", "litres", "liter", "liters" -> "LTR-LITRES";
            case "box", "boxes" -> "BOX-BOX";
            case "bag", "bags" -> "BAG-BAGS";
            case "bdl", "bundle", "bundles" -> "BDL-BUNDLES";
            case "roll", "rolls" -> "ROL-ROLLS";
            case "set", "sets" -> "SET-SETS";
            case "pkt", "packet", "packets", "pack", "packs" -> "PAC-PACKS";
            case "dz", "doz", "dozen" -> "DOZ-DOZENS";
            case "sqft", "sq ft", "sq.ft" -> "SQF-SQUARE FEET";
            case "sqm", "sq m", "sq.m" -> "SQM-SQUARE METERS";
            case "ton", "tons", "tonne", "tonnes" -> "TON-TONNES";
            case "qtl", "quintal" -> "QTL-QUINTAL";
            default -> "OTH-OTHERS";
        };
    }
}
