package com.hardware.erp.notification.entity;

public enum NotificationStatus {
    /** Actually handed to a real, configured provider (SMTP today; a real SMS/WhatsApp API once one is wired in). */
    SENT,
    /** No real provider configured for this channel - the message was logged, not delivered. */
    LOGGED_ONLY,
    /** A configured provider was called and it threw. */
    FAILED,
    /**
     * CR-056 - set only by a real Meta delivery-status webhook event
     * (WhatsAppWebhookController), never assumed. SENT means "Meta
     * accepted it"; this means "the recipient's device confirmed receipt."
     */
    DELIVERED,
    /** CR-056 - set only by a real Meta "read" webhook event, same rule as DELIVERED. */
    READ
}
