package com.hardware.erp.common.sequence;

/**
 * One constant per generated document code (CR-041).
 *
 * The prefix and width live here rather than as private constants scattered
 * across nine service implementations, which is how the same off-by-one race
 * came to be copy-pasted ten times in the first place. The values match the
 * codes already in the database exactly - changing one would orphan every
 * existing row from its own sequence, so they are effectively locked.
 */
public enum DocumentType {

    CUSTOMER("CUS-", 4),
    SUPPLIER("SUP-", 4),
    PRODUCT("PRD-", 6),
    CATEGORY("CAT-", 4),
    BRAND("BRD-", 4),
    INVOICE("INV-", 6),
    QUOTATION("QUO-", 6),
    PURCHASE("PUR-", 6),
    PROJECT("PRJ-", 4),
    SALES_ORDER("SO-", 6),
    DELIVERY_CHALLAN("DC-", 6),
    CREDIT_NOTE("CN-", 6);

    private final String prefix;
    private final int digits;

    DocumentType(String prefix, int digits) {
        this.prefix = prefix;
        this.digits = digits;
    }

    public String prefix() {
        return prefix;
    }

    public int digits() {
        return digits;
    }

    /** Formats an allocated number into the stored code, e.g. 419 -> "INV-000419". */
    public String format(long value) {
        return prefix + String.format("%0" + digits + "d", value);
    }
}
