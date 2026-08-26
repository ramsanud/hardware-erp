package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;

/**
 * CR-031 (Customer 360 §27-40) - what a tenant is currently using against
 * its tier's entitlement limits, for the Shop Settings usage dashboard.
 * A limit of -1 (SubscriptionTier.UNLIMITED) means no cap - the frontend
 * must render that as "Unlimited", never as a 0% or negative progress bar.
 */
public record UsageSummaryResponse(
        SubscriptionTier tier,
        long ownerCount, int maxOwners,
        long customerCount, int maxCustomers,
        long supplierCount, int maxSuppliers,
        long productCount, int maxProducts
) {}
