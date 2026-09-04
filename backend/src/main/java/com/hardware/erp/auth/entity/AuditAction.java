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
    BANK_ACCOUNT_REVEALED
}
