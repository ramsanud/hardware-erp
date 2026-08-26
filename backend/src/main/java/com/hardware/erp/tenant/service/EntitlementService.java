package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.dto.UsageSummaryResponse;

/**
 * CR-031 (Customer 360 §27-40) - the one sanctioned place to check a
 * tenant's subscription-tier entitlement limits before creating an owner,
 * customer, supplier or product, mirroring SubscriptionService's own
 * "one sanctioned way to gate a feature" pattern. Each requireCanAdd*()
 * throws BusinessException (402 Payment Required, ENTITLEMENT_LIMIT_REACHED)
 * when the tenant's active count is already at its tier's limit - called
 * before the row is built, so nothing partially writes.
 */
public interface EntitlementService {

    void requireCanAddOwner();

    void requireCanAddCustomer();

    void requireCanAddSupplier();

    void requireCanAddProduct();

    UsageSummaryResponse usageSummary();
}
