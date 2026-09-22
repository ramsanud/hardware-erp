package com.hardware.erp.subscription.entity;

/**
 * CR-088. What the shop may use in each state is decided in one place,
 * FeatureAccessServiceImpl.effectivePlan(): TRIAL, ACTIVE and PAST_DUE keep
 * the full plan (PAST_DUE is a grace period, not a lock-out); EXPIRED,
 * CANCELLED and SUSPENDED fall back to BASIC. Data is never deleted in any
 * state - DATA_EXPORT is a BASIC feature precisely so an expired shop can
 * still take its records away.
 */
public enum SubscriptionStatus {
    TRIAL, ACTIVE, PAST_DUE, EXPIRED, CANCELLED, SUSPENDED;

    public boolean grantsPaidFeatures() {
        return this == TRIAL || this == ACTIVE || this == PAST_DUE;
    }
}
