package com.hardware.erp.platformadmin.dto;

import java.util.List;

/**
 * Platform Admin Console, CR-057 phase 10 - Tenant Analytics. Every number
 * is a real aggregate query against the live database, run at request time -
 * see TenantAnalyticsService's own javadoc for exactly what each series is
 * derived from and its stated limitations (this class intentionally does
 * not claim more precision than the underlying data supports).
 */
public record TenantAnalyticsResponse(
        long activeTenantsNow,
        List<GrowthPoint> growth,
        List<ModuleUsagePoint> moduleUsage,
        List<ChurnPoint> churn
) {
    /** One calendar month, last 12 months. activeUsers is a real distinct-LOGIN_SUCCESS count, not app_user.last_login_at. */
    public record GrowthPoint(String month, long newTenants, long newUsers, long activeUsers) {}

    /** Snapshot as of now - of currently ACTIVE tenants, how many have at least one row in this module's own table. */
    public record ModuleUsagePoint(String module, long tenantsUsing, double adoptionPercent) {}

    /**
     * An approximation, stated as one: churnRatePercent is tenants suspended
     * this month (a real platform_audit_log TENANT_SUSPENDED event count)
     * divided by total tenants that exist by month end - not a cohort-based
     * or usage-based churn, since tenant.status carries no history and there
     * is no session/activity model to derive real engagement churn from.
     * Null when there were no tenants yet to divide by.
     */
    public record ChurnPoint(String month, long tenantsSuspended, long totalTenantsByMonthEnd, Double churnRatePercent) {}
}
