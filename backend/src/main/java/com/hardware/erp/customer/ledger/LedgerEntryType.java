package com.hardware.erp.customer.ledger;

/**
 * CR-091 Phase 4. What moved the customer's balance. Debit increases what
 * they owe, credit reduces it; each type is one or the other, never both.
 */
public enum LedgerEntryType {
    INVOICE(true),
    PAYMENT(false),
    SALES_RETURN(false),
    INVOICE_CANCELLATION(false),
    /** Manual, PAYMENT_MANAGE, always with a reason - either direction. */
    ADJUSTMENT(true);

    private final boolean debitByDefault;

    LedgerEntryType(boolean debitByDefault) {
        this.debitByDefault = debitByDefault;
    }

    public boolean isDebitByDefault() {
        return debitByDefault;
    }
}
