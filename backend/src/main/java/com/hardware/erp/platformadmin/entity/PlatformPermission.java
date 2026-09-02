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
    PLATFORM_ADMIN_MANAGE
}
