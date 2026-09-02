package com.hardware.erp.platformadmin.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The 7 platform-staff roles from the Platform Admin Console spec.
 *
 * Each role's permission set is fixed here, in code, reviewed the same way
 * as any other change - not stored in a database table an admin could edit.
 * Phase 1 only defines PLATFORM_ADMIN_MANAGE; later phases (tenant
 * management, support, security center, ...) add their own permissions to
 * the relevant roles here as those phases land, keeping least-privilege
 * explicit and auditable in one place.
 */
public enum PlatformAdminRole {

    /** Full control, including creating and managing other platform admins. */
    SUPER_ADMIN(EnumSet.of(PlatformPermission.PLATFORM_ADMIN_MANAGE)),
    PLATFORM_ADMIN(EnumSet.noneOf(PlatformPermission.class)),
    SUPPORT_ADMIN(EnumSet.noneOf(PlatformPermission.class)),
    SECURITY_ADMIN(EnumSet.noneOf(PlatformPermission.class)),
    FINANCE_ADMIN(EnumSet.noneOf(PlatformPermission.class)),
    DEVELOPER(EnumSet.noneOf(PlatformPermission.class)),
    READ_ONLY_AUDITOR(EnumSet.noneOf(PlatformPermission.class));

    private final Set<PlatformPermission> permissions;

    PlatformAdminRole(Set<PlatformPermission> permissions) {
        this.permissions = permissions;
    }

    public Set<PlatformPermission> permissions() {
        return permissions;
    }
}
