package com.hardware.erp.auth.entity;

/** Security events only. Business transaction history belongs to each module. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    LOGIN_MFA_REQUIRED,
    MFA_CHALLENGE_FAILED,
    MFA_ENROLLMENT_STARTED,
    MFA_ENROLLED,
    LOGOUT,
    LOGOUT_ALL,
    SESSION_REVOKED,
    TOKEN_REFRESHED,
    REFRESH_TOKEN_REUSE_DETECTED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    PASSWORD_RESET_BY_ADMIN,
    USER_CREATED,
    USER_UPDATED,
    USER_DEACTIVATED,
    /** CR-058. A soft-deleted account was restored, re-enabling its login. */
    USER_RESTORED,
    ROLE_CHANGED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    RATE_LIMIT_EXCEEDED,
    BOOTSTRAP_OWNER_CREATED,
    BANK_ACCOUNT_REVEALED,
    /** CR-067. A shop erased its own transactional data. The log survives the reset. */
    DATA_RESET,
    // CR-078 - codes by email.
    /** A code sent to the account's address was entered correctly for the first time. */
    EMAIL_VERIFIED,
    /** A code was wrong, expired, missing or exhausted; the reason names which. */
    EMAIL_OTP_FAILED,
    /** A signed-in user re-confirmed with a code before a sensitive action. */
    STEP_UP_VERIFIED,
    /** The login email on an account changed - it now needs verifying again. */
    EMAIL_CHANGED,
    /** An authenticator app was added from the profile by a user already signed in (CR-078). */
    MFA_ENROLLED_FROM_PROFILE
}
