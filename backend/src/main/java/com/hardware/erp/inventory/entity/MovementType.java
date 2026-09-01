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
    PURCHASE_RETURN,
    /** Stock leaving because a Delivery Challan was issued (CR-052) - goods physically moved without a tax invoice yet. */
    DELIVERY,
    /** Stock returning because a Delivery Challan was cancelled, or subsumed into an Invoice at DC->Invoice conversion. */
    DELIVERY_REVERSAL,
    /** Stock returning because a Credit Note was issued against an invoice (CR-052) - the customer physically returned goods. */
    SALES_RETURN,
    /** Stock leaving again because a Credit Note was itself cancelled - the paired reversal of SALES_RETURN. */
    SALES_RETURN_REVERSAL
}
