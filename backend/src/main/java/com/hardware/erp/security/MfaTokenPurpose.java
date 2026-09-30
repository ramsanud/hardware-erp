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
    ENROLL,
    /**
     * CR-078. Password check passed for a user with no authenticator app, and
     * a code was sent to their email instead. Verified by that code, never by
     * TOTP - the two purposes are distinct so a token issued for one cannot be
     * presented to the other's verifier.
     */
    LOGIN_EMAIL,
    /**
     * CR-078. An already signed-in user entered a code sent to their email.
     * Issued by /step-up/verify, consumed by the sensitive endpoints that
     * demand it. Not a session token: JwtAuthenticationFilter rejects it as
     * a bearer credential exactly as it rejects the other purposes.
     */
    STEP_UP
}
