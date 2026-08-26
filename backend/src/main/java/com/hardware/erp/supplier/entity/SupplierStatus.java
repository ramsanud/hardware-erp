package com.hardware.erp.supplier.entity;

public enum SupplierStatus {
    /** Normal. Purchase orders may be raised. */
    ACTIVE,
    /** No longer traded with. Historical documents remain. */
    INACTIVE,
    /** Deliberately barred - a dispute, or repeated quality problems. */
    BLOCKED
}
