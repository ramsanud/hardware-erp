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
    BOOTSTRAP_SUPER_ADMIN_CREATED,
    TENANT_SUSPENDED,
    TENANT_REACTIVATED,
    INCIDENT_OPENED,
    INCIDENT_AUTO_RESOLVED,
    INCIDENT_INVESTIGATING,
    INCIDENT_RESOLVED,
    INCIDENT_IGNORED,
    INCIDENT_REOPENED,
    SUPPORT_REPLIED,
    SUPPORT_INTERNAL_NOTE_ADDED,
    SUPPORT_ASSIGNED,
    SUPPORT_PRIORITY_CHANGED,
    SUPPORT_STATUS_CHANGED,
    JOB_RETRIED,
    FEATURE_FLAG_CREATED,
    FEATURE_FLAG_ENABLED,
    FEATURE_FLAG_DISABLED,
    FEATURE_FLAG_DELETED,
    PLATFORM_SETTING_UPDATED,

    /**
     * Backup Center (CR-057 phase 11) had no audit action at all until
     * CR-059: downloading one tenant's entire customer/supplier/invoice
     * dataset is the most sensitive thing this console can do, and it left
     * no trace in platform_audit_log while a feature-flag toggle left three.
     *
     * Three actions, not one with a success flag, so the Audit Log viewer's
     * action filter can separate an attempt from its outcome - the same
     * split LOGIN_SUCCESS/LOGIN_FAILURE already uses. REQUESTED is written
     * before the tenant is even resolved, so a probe against a tenant id
     * that does not exist is evidenced too, and every REQUESTED row is
     * always followed by exactly one COMPLETED or FAILED row.
     */
    TENANT_EXPORT_REQUESTED,
    TENANT_EXPORT_COMPLETED,
    TENANT_EXPORT_FAILED
}
