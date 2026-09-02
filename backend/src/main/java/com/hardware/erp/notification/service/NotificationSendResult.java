package com.hardware.erp.notification.service;

import com.hardware.erp.notification.entity.NotificationStatus;

/**
 * Task 05 (WhatsApp reminders). {@code providerMessageId} is null for
 * {@link NotificationStatus#LOGGED_ONLY}/{@link NotificationStatus#FAILED}
 * - only a real accepted send returns one, kept for future delivery-status
 * reconciliation (see {@code notification_log.provider_message_id}).
 */
public record NotificationSendResult(NotificationStatus status, String providerMessageId) {

    public static NotificationSendResult loggedOnly() {
        return new NotificationSendResult(NotificationStatus.LOGGED_ONLY, null);
    }

    public static NotificationSendResult sent(String providerMessageId) {
        return new NotificationSendResult(NotificationStatus.SENT, providerMessageId);
    }

    public static NotificationSendResult failed() {
        return new NotificationSendResult(NotificationStatus.FAILED, null);
    }
}
