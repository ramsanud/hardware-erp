package com.hardware.erp.platformadmin.entity;

/**
 * Informational only in this pass - describes the *intended* audience of
 * a flag, not an enforced targeting rule. TENANT/PLAN differential
 * override storage (a real per-tenant exception table) is not built here;
 * see FeatureFlagService's own javadoc for the honest scope of what
 * isEnabled() actually checks.
 */
public enum FeatureFlagScope {
    GLOBAL, TENANT, PLAN
}
