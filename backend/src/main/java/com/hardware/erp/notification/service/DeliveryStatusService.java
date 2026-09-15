package com.hardware.erp.notification.service;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;

/**
 * CR-085. Advances a notification_log row when a provider reports what became
 * of a message it accepted. One implementation serves every provider webhook
 * (Meta, Twilio, SendGrid) so the forward-only rule - SENT to DELIVERED to
 * READ, FAILED only from SENT, never backwards - lives in exactly one place.
 */
public interface DeliveryStatusService {

    /**
     * Tenant-scoped, for a provider whose message ids are per account (Meta:
     * the phone_number_id names the tenant).
     */
    void apply(Long tenantId, String providerMessageId, NotificationStatus incoming);

    /**
     * Channel-scoped, for a provider the platform runs for every tenant
     * (Twilio, SendGrid, CR-074): the message id is globally unique at the
     * provider and there is no tenant in the callback, so the row is found by
     * channel and id. A guessed id cannot cross tenants because the id space
     * is the provider's, not the caller's.
     */
    void apply(NotificationChannel channel, String providerMessageId, NotificationStatus incoming);
}
