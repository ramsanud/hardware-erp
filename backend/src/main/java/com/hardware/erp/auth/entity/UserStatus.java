package com.hardware.erp.auth.entity;

public enum UserStatus {
    /** Can sign in. */
    ACTIVE,
    /** Cannot sign in. Normal state for a former employee. */
    INACTIVE,
    /** Cannot sign in. Blocked by the owner pending investigation. */
    SUSPENDED
}
