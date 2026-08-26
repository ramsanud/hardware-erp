package com.hardware.erp.inventory.entity;

public enum MovementType {
    /** The very first stock row created for a product, quantity zero. */
    INITIAL,
    /** A manual correction - stock take, damage, found stock. */
    ADJUSTMENT,
    /** Stock leaving because an invoice was created. */
    SALE,
    /** Stock returning because an invoice was cancelled. */
    SALE_REVERSAL,
    /** Stock arriving because a Purchase was received (manual or Supplier Bill Import). */
    PURCHASE_RECEIPT,
    /** Stock leaving because a Purchase was cancelled - the paired reversal of PURCHASE_RECEIPT, mirroring SALE/SALE_REVERSAL. */
    PURCHASE_RETURN
}
