package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.entity.SubscriptionTier;

/** The one sanctioned way to gate a feature behind a subscription tier (CR-027) - mirrors SecurityUtils' permission checks. */
public interface SubscriptionService {

    SubscriptionTier currentTier();

    /** Throws BusinessException (402 Payment Required) if the caller's tenant is below the given tier. */
    void requireTier(SubscriptionTier minimum);
}
