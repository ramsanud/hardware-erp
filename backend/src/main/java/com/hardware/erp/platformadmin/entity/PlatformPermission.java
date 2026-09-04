package com.hardware.erp.platformadmin.entity;

/**
 * Compile-time authorities for the Platform Admin Console.
 *
 * Unlike the tenant side's PermissionCode, there is no database permission
 * table behind this: the 7 platform-admin roles are a fixed, code-defined
 * set (never tenant-configurable), so PlatformAdminRole.permissions() is the
 * single source of truth. New phases add constants here and wire them into
 * the role map in PlatformAdminRole - never a hardcoded frontend check.
 */
public enum PlatformPermission {

    /** Create and list platform admin accounts. SUPER_ADMIN only in Phase 1. */
    PLATFORM_ADMIN_MANAGE,

    /** Phase 2 - list/search tenants, view a tenant's detail and usage. Every role gets this; it is the minimum a platform staff member needs to do anything useful. */
    TENANT_VIEW,

    /** Phase 2 - suspend/reactivate a tenant. Withheld from read-only and analytics-only roles. */
    TENANT_MANAGE,

    /** Phase 2b - platform-wide user list/detail (not one tenant's own user management, which stays tenant-side). */
    USER_VIEW,

    /** Phase 3 - System Health dashboard + Incident list (read-only). */
    SYSTEM_HEALTH_VIEW,

    /** Phase 3 - transition an incident's status (investigating/resolve/ignore/reopen). */
    INCIDENT_MANAGE,

    /** Phase 4 - view support tickets. */
    SUPPORT_VIEW,

    /** Phase 4 - reply, assign, change priority/status, internal notes. */
    SUPPORT_MANAGE,

    /** Phase 5 - view subscription/billing state. */
    BILLING_VIEW,

    /** Phase 5 - change a tenant's plan directly (not the same as a customer-verified Razorpay payment). */
    BILLING_MANAGE,

    /** Phase 6 - security dashboard, security events, session list. */
    SECURITY_VIEW,

    /** Phase 6 - revoke a session. */
    SECURITY_MANAGE,

    /** Phase 6 - global audit log viewer. */
    AUDIT_VIEW,

    /** Phase 7 - API/DB/background-job diagnostics (read-only). */
    DEVELOPER_TOOLS_VIEW,

    /** Phase 7 - retry a failed job. */
    DEVELOPER_TOOLS_MANAGE,

    /** Phase 8 - view tenant export/backup history. */
    BACKUP_VIEW,

    /** Phase 8 - trigger a tenant export. */
    BACKUP_MANAGE,

    /** Feature flags - view. */
    FEATURE_FLAG_VIEW,

    /** Feature flags - create/toggle/delete. */
    FEATURE_FLAG_MANAGE,

    /** Announcements - view. */
    ANNOUNCEMENT_VIEW,

    /** Announcements - create/edit/publish/expire. */
    ANNOUNCEMENT_MANAGE,

    /** Tenant Analytics dashboard + charts (read-only). */
    ANALYTICS_VIEW,

    /** Tenant Analytics PDF/XLSX/CSV export. */
    ANALYTICS_EXPORT
}
