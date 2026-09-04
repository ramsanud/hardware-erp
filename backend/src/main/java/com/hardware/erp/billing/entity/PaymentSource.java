package com.hardware.erp.billing.entity;

/** Which path first recorded a payment - the client-side /verify callback, or the async webhook. Either can arrive first. */
public enum PaymentSource {
    CLIENT_VERIFY,
    WEBHOOK
}
