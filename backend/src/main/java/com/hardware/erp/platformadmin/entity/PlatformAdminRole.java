package com.hardware.erp.platformadmin.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The 7 platform-staff roles from the Platform Admin Console spec.
 *
 * Each role's permission set is fixed here, in code, reviewed the same way
 * as any other change - not stored in a database table an admin could edit.
 * SUPER_ADMIN holds everything, including PLATFORM_ADMIN_MANAGE (creating
 * other admins), which no other role gets. PLATFORM_ADMIN is otherwise
 * near-SUPER_ADMIN - the day-to-day operator role. Every other role holds
 * the *_VIEW permission for areas outside its own specialty (so, e.g.,
 * SECURITY_ADMIN can still see a tenant exists) but only the *_MANAGE
 * permissions for its own domain. READ_ONLY_AUDITOR holds every *_VIEW
 * permission and zero *_MANAGE ones, by design.
 */
public enum PlatformAdminRole {

    /** Full control, including creating and managing other platform admins. */
    SUPER_ADMIN(EnumSet.allOf(PlatformPermission.class)),

    /** Day-to-day operator - everything except creating other platform admins. */
    PLATFORM_ADMIN(complementOf(PlatformPermission.PLATFORM_ADMIN_MANAGE)),

    SUPPORT_ADMIN(EnumSet.of(
            PlatformPermission.TENANT_VIEW, PlatformPermission.TENANT_MANAGE,
            PlatformPermission.USER_VIEW,
            PlatformPermission.SUPPORT_VIEW, PlatformPermission.SUPPORT_MANAGE,
            PlatformPermission.SYSTEM_HEALTH_VIEW,
            PlatformPermission.AUDIT_VIEW,
            PlatformPermission.ANALYTICS_VIEW,
            PlatformPermission.ANNOUNCEMENT_VIEW)),

    SECURITY_ADMIN(EnumSet.of(
            PlatformPermission.TENANT_VIEW, PlatformPermission.TENANT_MANAGE,
            PlatformPermission.USER_VIEW,
            PlatformPermission.SECURITY_VIEW, PlatformPermission.SECURITY_MANAGE,
            PlatformPermission.AUDIT_VIEW,
            PlatformPermission.SYSTEM_HEALTH_VIEW, PlatformPermission.INCIDENT_MANAGE,
            PlatformPermission.ANALYTICS_VIEW)),

    FINANCE_ADMIN(EnumSet.of(
            PlatformPermission.TENANT_VIEW,
            PlatformPermission.USER_VIEW,
            PlatformPermission.BILLING_VIEW, PlatformPermission.BILLING_MANAGE,
            PlatformPermission.ANALYTICS_VIEW, PlatformPermission.ANALYTICS_EXPORT,
            PlatformPermission.AUDIT_VIEW)),

    DEVELOPER(EnumSet.of(
            PlatformPermission.TENANT_VIEW,
            PlatformPermission.USER_VIEW,
            PlatformPermission.SYSTEM_HEALTH_VIEW, PlatformPermission.INCIDENT_MANAGE,
            PlatformPermission.DEVELOPER_TOOLS_VIEW, PlatformPermission.DEVELOPER_TOOLS_MANAGE,
            PlatformPermission.FEATURE_FLAG_VIEW, PlatformPermission.FEATURE_FLAG_MANAGE,
            PlatformPermission.AUDIT_VIEW,
            PlatformPermission.ANALYTICS_VIEW)),

    /** Every *_VIEW permission, zero *_MANAGE ones - by design, not an oversight. */
    READ_ONLY_AUDITOR(EnumSet.of(
            PlatformPermission.TENANT_VIEW,
            PlatformPermission.USER_VIEW,
            PlatformPermission.SYSTEM_HEALTH_VIEW,
            PlatformPermission.SUPPORT_VIEW,
            PlatformPermission.BILLING_VIEW,
            PlatformPermission.SECURITY_VIEW,
            PlatformPermission.AUDIT_VIEW,
            PlatformPermission.DEVELOPER_TOOLS_VIEW,
            PlatformPermission.BACKUP_VIEW,
            PlatformPermission.FEATURE_FLAG_VIEW,
            PlatformPermission.ANNOUNCEMENT_VIEW,
            PlatformPermission.ANALYTICS_VIEW));

    private static Set<PlatformPermission> complementOf(PlatformPermission excluded) {
        Set<PlatformPermission> all = EnumSet.allOf(PlatformPermission.class);
        all.remove(excluded);
        return all;
    }

    private final Set<PlatformPermission> permissions;

    PlatformAdminRole(Set<PlatformPermission> permissions) {
        this.permissions = permissions;
    }

    public Set<PlatformPermission> permissions() {
        return permissions;
    }
}
