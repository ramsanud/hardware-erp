package com.hardware.erp.billing.service;

import com.hardware.erp.billing.dto.SubscriptionOrderResponse;
import com.hardware.erp.billing.dto.TenantBillingHistoryResponse;
import com.hardware.erp.billing.dto.VerifyPaymentRequest;
import com.hardware.erp.tenant.entity.SubscriptionTier;

public interface SubscriptionBillingService {

    /** Creates a Razorpay order for the current tenant to upgrade to requestedTier. Throws if billing is not configured. */
    SubscriptionOrderResponse createOrder(SubscriptionTier requestedTier);

    /**
     * Client-side verification per Razorpay Checkout's own callback contract:
     * HMAC-SHA256(order_id + "|" + payment_id, key_secret) must equal the
     * signature Checkout handed back. Only on a match does this apply the
     * tenant's tier upgrade - never on the client's say-so alone.
     */
    void verifyPayment(VerifyPaymentRequest request);

    /** Raw webhook body + its X-Razorpay-Signature header. Returns true if the signature verified. */
    boolean handleWebhook(String rawBody, String signatureHeader);

    TenantBillingHistoryResponse history();
}
