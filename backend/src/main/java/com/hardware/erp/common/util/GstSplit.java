package com.hardware.erp.common.util;

/**
 * CR-091 Phase 1. The ONE place a GST amount is split into CGST/SGST or
 * IGST, and the ONE place "is this supply inter-state?" is decided.
 *
 * Both rules pre-date this class - the split has been InvoicePdfService's
 * since Module 11 (half rounded down to CGST, remainder to SGST, so the
 * two halves always sum to the line's GST to the paisa), and the
 * place-of-supply precedence is the one CR-087's GSTR-1 export uses:
 * the customer's state, else the state in the customer's GSTIN, else the
 * shop's own state (intra-state). CR-091 moves them here so the PDF, the
 * stored per-line columns, the reports and the GSTR-1 file read the same
 * two functions and can never disagree.
 *
 * Every amount is paise (long). Never a double, never a BigDecimal with
 * scale - hard rule: money is BIGINT paise.
 */
public final class GstSplit {

    private GstSplit() {
    }

    public enum SupplyType {
        /** Same state - CGST + SGST. */
        INTRA,
        /** Different state - IGST. */
        INTER
    }

    /** The three tax components for one amount. Exactly one of (cgst+sgst) or igst is non-zero. */
    public record Split(long cgstPaise, long sgstPaise, long igstPaise) {
        public long total() {
            return cgstPaise + sgstPaise + igstPaise;
        }
    }

    /**
     * Where the supply goes. Precedence is the GSTR-1 rule: the buyer's
     * recorded state, else the first two characters of their GSTIN, else
     * the shop's own state. A blank everywhere is intra-state - a shop
     * that has not entered its own state cannot be charged IGST by
     * accident.
     */
    public static String placeOfSupply(String customerStateCode, String customerGstin, String shopStateCode) {
        if (present(customerStateCode)) {
            return customerStateCode.trim();
        }
        if (customerGstin != null && customerGstin.trim().length() >= 2) {
            return customerGstin.trim().substring(0, 2);
        }
        return present(shopStateCode) ? shopStateCode.trim() : null;
    }

    public static SupplyType supplyType(String shopStateCode, String placeOfSupplyStateCode) {
        if (!present(shopStateCode) || !present(placeOfSupplyStateCode)) {
            return SupplyType.INTRA;
        }
        return shopStateCode.trim().equals(placeOfSupplyStateCode.trim()) ? SupplyType.INTRA : SupplyType.INTER;
    }

    /**
     * Half rounded down to CGST, the remainder to SGST, so an odd paisa
     * lands on SGST and the two always sum to the input. IGST takes the
     * whole amount inter-state.
     */
    public static Split split(long gstPaise, SupplyType supplyType) {
        if (supplyType == SupplyType.INTER) {
            return new Split(0L, 0L, gstPaise);
        }
        long cgst = gstPaise / 2;
        return new Split(cgst, gstPaise - cgst, 0L);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
