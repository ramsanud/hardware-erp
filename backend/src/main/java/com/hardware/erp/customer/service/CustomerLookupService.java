package com.hardware.erp.customer.service;

import com.hardware.erp.customer.entity.Customer;

public interface CustomerLookupService {

    /**
     * Matched by mobile number per tenant (CR-021). Used by both Invoice and
     * Quotation (CR-022) - the same person buying, or asking for a quote,
     * again must reuse their existing customer row, not create a duplicate.
     * A returning customer's GST/state can be filled in or corrected on any
     * later call.
     */
    Customer findOrCreate(String name, String mobile, String email,
                           String gstNo, String stateCode, Long tenantId);
}
