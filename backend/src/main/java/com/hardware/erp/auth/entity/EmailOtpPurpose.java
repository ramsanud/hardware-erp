package com.hardware.erp.auth.entity;

/**
 * CR-078 - what a code sent by email is allowed to prove. Mirrors the CHECK
 * constraint on email_otp.purpose; a code issued for one purpose never
 * satisfies another, which is enforced by the lookup, not by convention.
 */
public enum EmailOtpPurpose {
    /** Proving an address at shop registration, or after a profile email change. */
    EMAIL_VERIFY,
    /** The second factor at sign-in for a user with no authenticator app. */
    LOGIN,
    /** Forgot-password, as an alternative to the emailed link. */
    PASSWORD_RESET,
    /** Re-confirming an already signed-in user before a sensitive action. */
    STEP_UP
}
