package com.hardware.erp.tenant.entity;

public enum TenantStatus {
    /** Normal. Users of this shop may sign in. */
    ACTIVE,
    /** Every user of this shop is blocked from signing in, without touching their rows. */
    SUSPENDED
}
