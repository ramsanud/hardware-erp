package com.hardware.erp.subscription.service;

import com.hardware.erp.subscription.dto.CurrentSubscriptionResponse;
import com.hardware.erp.subscription.dto.SubscriptionPlanResponse;
import com.hardware.erp.subscription.entity.SubscriptionStatus;
import com.hardware.erp.subscription.entity.TenantSubscription;
import com.hardware.erp.tenant.entity.SubscriptionTier;

import java.util.List;

/**
 * CR-088. Every plan/status transition goes through here so
 * tenant.subscription_tier, tenant_subscription and subscription_history
 * can never disagree. The CR-027 settings picker, the CR-032 coupon and
 * the CR-057 Razorpay verify all call applyTier() instead of touching the
 * tenant row themselves.
 */
public interface SubscriptionLifecycleService {

    List<SubscriptionPlanResponse> plans();

    CurrentSubscriptionResponse current();

    /** Self-service move to a plan. Upgrades are refused with UPGRADE_REQUIRES_CHECKOUT when a payment gateway is configured. */
    CurrentSubscriptionResponse changePlan(String planCode, String reason);

    /** Cancels at once: status CANCELLED, plan falls back to BASIC for feature purposes, data untouched. */
    CurrentSubscriptionResponse cancel(String reason);

    /**
     * Sets the shop's tier (and therefore plan) from any of the sanctioned
     * callers - checkout verification, coupon redemption, the picker, the
     * trial expiry. Writes the history row. Trial end is optional.
     */
    TenantSubscription applyTier(Long tenantId, SubscriptionTier tier, SubscriptionStatus status,
                                 java.time.LocalDateTime trialEndsAt, String reason, String paymentReference);

    /** New shop: the configured trial (app.subscription.trial-days / trial-tier), or BASIC ACTIVE when trial-days is 0. */
    TenantSubscription startForNewTenant(Long tenantId);

    /**
     * New shop that explicitly chose a tier at registration (the legacy
     * self-declared TenantRegistrationRequest.subscriptionTier field) -
     * ACTIVE on that tier immediately, no trial. Runs in the caller's own
     * transaction exactly like startForNewTenant(Long), for the same
     * uncommitted-tenant-row reason.
     */
    TenantSubscription startForNewTenant(Long tenantId, SubscriptionTier explicitTier);

    /** Row for the tenant, created as ACTIVE-on-current-tier if missing; lazy TRIAL/ACTIVE expiry applied. */
    TenantSubscription currentFor(Long tenantId);
}
