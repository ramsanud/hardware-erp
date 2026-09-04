package com.hardware.erp.security;

/**
 * What a short-lived MFA challenge token proves the holder already did.
 * Deliberately a separate enum from platformadmin.security.MfaTokenPurpose
 * even though the two are identical in shape - tenant and platform-admin
 * auth are two structurally separate systems by design (CR-016/CR-054),
 * and sharing this tiny enum would be the one thread coupling them.
 */
public enum MfaTokenPurpose {
    /** Password check passed for a user who already has MFA enabled. */
    LOGIN,
    /** Password check passed for a user who must enroll before getting a session. */
    ENROLL
}
