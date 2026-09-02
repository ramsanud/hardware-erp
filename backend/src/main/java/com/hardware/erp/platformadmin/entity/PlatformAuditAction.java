package com.hardware.erp.platformadmin.entity;

/** The platform-wide equivalent of AuditAction. Kept separate - see V39 migration comment. */
public enum PlatformAuditAction {
    LOGIN_SUCCESS,
    LOGIN_MFA_REQUIRED,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    MFA_CHALLENGE_FAILED,
    MFA_ENROLLMENT_STARTED,
    MFA_ENROLLED,
    BACKUP_CODE_USED,
    LOGOUT,
    LOGOUT_ALL,
    TOKEN_REFRESHED,
    REFRESH_TOKEN_REUSE_DETECTED,
    PLATFORM_ADMIN_CREATED,
    BOOTSTRAP_SUPER_ADMIN_CREATED
}
