package com.hardware.erp.subscription.service;

import com.hardware.erp.subscription.dto.FeatureAccessResponse;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.entity.SubscriptionPlan;

import java.util.Set;

/**
 * CR-088. The one sanctioned way to ask "may this shop use feature X?".
 * Controllers and services call requireFeature(); nothing anywhere else
 * compares a plan code. Mirrors SubscriptionService.requireTier (CR-027),
 * which stays for the tier-ordinal cases that predate the catalogue.
 */
public interface FeatureAccessService {

    /** The plan whose features apply right now - the shop's plan, or BASIC when its subscription is expired/cancelled/suspended. */
    SubscriptionPlan effectivePlan(Long tenantId);

    boolean hasFeature(Long tenantId, FeatureKey feature);

    /** Throws FeatureNotAvailableException (403 FEATURE_NOT_AVAILABLE) for the caller's tenant. */
    void requireFeature(FeatureKey feature);

    Set<FeatureKey> featuresOf(String planCode);

    FeatureAccessResponse access(Long tenantId, FeatureKey feature);

    /** Cheapest active plan that carries the feature, or null if no plan does. */
    SubscriptionPlan cheapestPlanWith(FeatureKey feature);

    /** Drops the cached plan-feature matrix - call after a platform operator edits plan_feature. */
    void refresh();
}
