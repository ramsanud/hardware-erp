package com.hardware.erp.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * CR-053 backlog item 2. A pure, stateless string builder for Tally's XML
 * import format - no XML library, hand-built exactly like
 * InvoicePdfService builds HTML, for the same reason: a template this
 * shaped is clearer as string concatenation than as a generic DOM API.
 *
 * Scope, stated plainly: this produces ledger-level accounting vouchers
 * (party + sales/purchase account + tax ledgers) and party/stock-item
 * masters - not "Invoice mode" vouchers with per-line inventory
 * allocations. A shop that needs item-wise stock inside Tally itself will
 * still need to enter that manually; this gets the books of accounts and
 * GST figures in correctly, which is the more common ask (CR-053 backlog
 * item 2's own scoping: "real, bounded, no external dependency").
 *
 * The debit/credit sign convention (ISDEEMEDPOSITIVE + the amount's own
 * sign) follows the convention used throughout Tally's own published XML
 * samples. It has not been verified against a real Tally installation -
 * no such software exists in this environment - so treat the first import
 * as a trial run against a scratch/test company before trusting it on a
 * real one.
 */
final class TallyXmlBuilder {

    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private TallyXmlBuilder() {
    }

    static String envelopeStart(String companyName) {
        return "<ENVELOPE>\n"
            + " <HEADER>\n"
            + "  <TALLYREQUEST>Import Data</TALLYREQUEST>\n"
            + " </HEADER>\n"
            + " <BODY>\n"
            + "  <IMPORTDATA>\n"
            + "   <REQUESTDESC>\n"
            + "    <REPORTNAME>All Masters</REPORTNAME>\n"
            + "    <STATICVARIABLES>\n"
            + "     <SVCURRENTCOMPANY>" + escape(companyName) + "</SVCURRENTCOMPANY>\n"
            + "    </STATICVARIABLES>\n"
            + "   </REQUESTDESC>\n"
            + "   <REQUESTDATA>\n";
    }

    static String envelopeEnd() {
        return "   </REQUESTDATA>\n"
            + "  </IMPORTDATA>\n"
            + " </BODY>\n"
            + "</ENVELOPE>\n";
    }

    /** One TALLYMESSAGE wraps every master and voucher - Tally accepts any number of them inside one REQUESTDATA. */
    static String messageStart() {
        return "    <TALLYMESSAGE xmlns:UDF=\"TallyUDF\">\n";
    }

    static String messageEnd() {
        return "    </TALLYMESSAGE>\n";
    }

    /**
     * A party ledger - Sundry Debtors for a customer, Sundry Creditors for
     * a supplier. Tally treats a ledger with a NAME it already has as an
     * update, not a duplicate, so re-running an export for an overlapping
     * date range is safe to re-import.
     */
    static String partyLedger(String name, String parentGroup, String gstin, String stateName) {
        StringBuilder sb = new StringBuilder();
        sb.append("     <LEDGER NAME=\"").append(escape(name)).append("\" ACTION=\"Create\">\n");
        sb.append("      <PARENT>").append(escape(parentGroup)).append("</PARENT>\n");
        if (stateName != null) {
            sb.append("      <LEDSTATENAME>").append(escape(stateName)).append("</LEDSTATENAME>\n");
        }
        if (gstin != null && !gstin.isBlank()) {
            sb.append("      <PARTYGSTIN>").append(escape(gstin)).append("</PARTYGSTIN>\n");
            sb.append("      <GSTREGISTRATIONTYPE>Regular</GSTREGISTRATIONTYPE>\n");
        } else {
            sb.append("      <GSTREGISTRATIONTYPE>Unregistered</GSTREGISTRATIONTYPE>\n");
        }
        sb.append("      <ISBILLWISEON>Yes</ISBILLWISEON>\n");
        sb.append("      <OPENINGBALANCE>0</OPENINGBALANCE>\n");
        sb.append("     </LEDGER>\n");
        return sb.toString();
    }

    /** A fixed system ledger this export always references - Sales/Purchase Account, CGST, SGST, IGST. Idempotent to re-import for the same reason as partyLedger. */
    static String systemLedger(String name, String parentGroup) {
        return "     <LEDGER NAME=\"" + escape(name) + "\" ACTION=\"Create\">\n"
            + "      <PARENT>" + escape(parentGroup) + "</PARENT>\n"
            + "      <OPENINGBALANCE>0</OPENINGBALANCE>\n"
            + "     </LEDGER>\n";
    }

    static String stockItemMaster(String name, String unit, String hsnCode, BigDecimal gstRatePercent) {
        StringBuilder sb = new StringBuilder();
        sb.append("     <STOCKITEM NAME=\"").append(escape(name)).append("\" ACTION=\"Create\">\n");
        sb.append("      <PARENT>Primary</PARENT>\n");
        sb.append("      <BASEUNITS>").append(escape(unit)).append("</BASEUNITS>\n");
        if (hsnCode != null && !hsnCode.isBlank()) {
            sb.append("      <HSNCODE>").append(escape(hsnCode)).append("</HSNCODE>\n");
        }
        if (gstRatePercent != null) {
            sb.append("      <GSTAPPLICABLE>Applicable</GSTAPPLICABLE>\n");
            sb.append("      <GSTRATE>").append(gstRatePercent.stripTrailingZeros().toPlainString()).append("</GSTRATE>\n");
        }
        sb.append("     </STOCKITEM>\n");
        return sb.toString();
    }

    /**
     * A Sales voucher. Party carries ISDEEMEDPOSITIVE=Yes with a NEGATIVE
     * amount; Sales Account and the tax ledgers carry ISDEEMEDPOSITIVE=No
     * with POSITIVE amounts - the standard Tally XML sign convention,
     * which sums to zero across the entry list (a Tally XML import
     * requirement, not a stylistic choice).
     */
    static String salesVoucher(LocalDate date, String voucherNumber, String partyName, String narration,
                               BigDecimal taxableAmount, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                               BigDecimal total) {
        StringBuilder sb = new StringBuilder();
        sb.append(voucherHeader("Sales", date, voucherNumber, partyName, narration));
        sb.append(ledgerEntry(partyName, true, total.negate()));
        sb.append(ledgerEntry("Sales Account", false, taxableAmount));
        if (cgst.signum() > 0) sb.append(ledgerEntry("CGST", false, cgst));
        if (sgst.signum() > 0) sb.append(ledgerEntry("SGST", false, sgst));
        if (igst.signum() > 0) sb.append(ledgerEntry("IGST", false, igst));
        sb.append("     </VOUCHER>\n");
        return sb.toString();
    }

    /** Mirrors salesVoucher with the sides swapped - see its own comment for the sign convention. */
    static String purchaseVoucher(LocalDate date, String voucherNumber, String partyName, String narration,
                                  BigDecimal taxableAmount, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                                  BigDecimal total) {
        StringBuilder sb = new StringBuilder();
        sb.append(voucherHeader("Purchase", date, voucherNumber, partyName, narration));
        sb.append(ledgerEntry(partyName, false, total));
        sb.append(ledgerEntry("Purchase Account", true, taxableAmount.negate()));
        if (cgst.signum() > 0) sb.append(ledgerEntry("CGST", true, cgst.negate()));
        if (sgst.signum() > 0) sb.append(ledgerEntry("SGST", true, sgst.negate()));
        if (igst.signum() > 0) sb.append(ledgerEntry("IGST", true, igst.negate()));
        sb.append("     </VOUCHER>\n");
        return sb.toString();
    }

    private static String voucherHeader(String vchType, LocalDate date, String voucherNumber,
                                        String partyName, String narration) {
        StringBuilder sb = new StringBuilder();
        sb.append("     <VOUCHER VCHTYPE=\"").append(vchType).append("\" ACTION=\"Create\">\n");
        sb.append("      <DATE>").append(date.format(TALLY_DATE)).append("</DATE>\n");
        sb.append("      <EFFECTIVEDATE>").append(date.format(TALLY_DATE)).append("</EFFECTIVEDATE>\n");
        sb.append("      <VOUCHERTYPENAME>").append(vchType).append("</VOUCHERTYPENAME>\n");
        sb.append("      <VOUCHERNUMBER>").append(escape(voucherNumber)).append("</VOUCHERNUMBER>\n");
        sb.append("      <PARTYLEDGERNAME>").append(escape(partyName)).append("</PARTYLEDGERNAME>\n");
        sb.append("      <NARRATION>").append(escape(narration)).append("</NARRATION>\n");
        return sb.toString();
    }

    private static String ledgerEntry(String ledgerName, boolean deemedPositive, BigDecimal amountRupees) {
        return "      <ALLLEDGERENTRIES.LIST>\n"
            + "       <LEDGERNAME>" + escape(ledgerName) + "</LEDGERNAME>\n"
            + "       <ISDEEMEDPOSITIVE>" + (deemedPositive ? "Yes" : "No") + "</ISDEEMEDPOSITIVE>\n"
            + "       <AMOUNT>" + amountRupees.setScale(2, RoundingMode.HALF_UP).toPlainString() + "</AMOUNT>\n"
            + "      </ALLLEDGERENTRIES.LIST>\n";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
