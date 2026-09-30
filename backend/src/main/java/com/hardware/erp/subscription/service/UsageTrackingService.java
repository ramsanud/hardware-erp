package com.hardware.erp.subscription.service;

import com.hardware.erp.subscription.dto.UsageResponse;
import com.hardware.erp.subscription.entity.UsageKey;

/**
 * CR-088 §15. Metering for the external services that cost money per unit.
 * Every send/request path that reaches a paid provider calls tryConsume()
 * BEFORE the provider, and stops if it returns false - the platform never
 * silently pays for a shop past its included count.
 */
public interface UsageTrackingService {

    /** Grants one unit if the shop is under its monthly included count. False means refused and NOT counted. */
    boolean tryConsume(Long tenantId, UsageKey usageKey);

    boolean tryConsume(Long tenantId, UsageKey usageKey, long units);

    /** Same as tryConsume but throws UsageLimitReachedException (429) when refused. */
    void consumeOrThrow(Long tenantId, UsageKey usageKey);

    UsageResponse usage(Long tenantId);
}
