package com.hardware.erp.billing.service;

/** Thin wrapper over the Razorpay Orders REST API - the only outbound HTTP call this module makes. */
public interface RazorpayOrderClient {

    /**
     * Creates an order via Razorpay's Orders API and returns its
     * razorpay-issued order id (e.g. "order_ABC123"). keyId/keySecret are
     * passed in rather than read from this class's own config, since the
     * caller (SubscriptionBillingService) is the one place that already
     * resolved the effective database-vs-environment credentials via
     * RazorpayConfigResolver - see its own javadoc for the precedence rule.
     */
    String createOrder(String keyId, String keySecret, String apiBaseUrl, long amountPaise, String currency, String receipt);
}
