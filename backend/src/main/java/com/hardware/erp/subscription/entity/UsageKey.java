package com.hardware.erp.subscription.entity;

import com.hardware.erp.notification.entity.NotificationChannel;

/** CR-088. The metered external services - each costs real money per unit, so none is unlimited on any plan. */
public enum UsageKey {
    WHATSAPP, SMS, EMAIL, AI_REQUEST, STORAGE_MB;

    public static UsageKey forChannel(NotificationChannel channel) {
        return switch (channel) {
            case WHATSAPP -> WHATSAPP;
            case SMS -> SMS;
            case EMAIL -> EMAIL;
        };
    }
}
