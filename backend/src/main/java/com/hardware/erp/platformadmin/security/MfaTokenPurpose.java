package com.hardware.erp.platformadmin.security;

/** What a short-lived MFA challenge token proves the holder already did. */
public enum MfaTokenPurpose {
    /** Password check passed for an account that already has MFA enabled. */
    LOGIN,
    /** Password check passed for an account that must enroll before it gets a session. */
    ENROLL
}
