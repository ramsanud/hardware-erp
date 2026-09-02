package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * There is no SMS provider account/API key available yet, so this is a
 * logging stub - not a fake "sent" response. Every call is logged at INFO
 * with the full intended message and returns normally; callers
 * (NotificationServiceImpl) never have to special-case "SMS isn't
 * configured yet".
 *
 * Renamed from SmsWhatsAppNotificationProvider under Task 05 (WhatsApp
 * reminders, MUST-HAVE) - WhatsApp used to share this class (identical
 * behaviour, since neither had a real provider), but now has its own
 * real-capable implementation, {@link WhatsAppBusinessProvider}. Two
 * {@link NotificationProvider} beans both claiming the same
 * {@link NotificationChannel} would silently collide in
 * NotificationServiceImpl's channel-to-provider map (whichever bean Spring
 * iterates last wins, with no error) - so this class's own
 * {@link #supportedChannels()} narrowing to SMS alone is load-bearing, not
 * a cleanup.
 */
@Slf4j
@Component
public class SmsNotificationProvider implements NotificationProvider {

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.SMS);
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body) {
        log.info("SMS provider not configured - message logged instead of sent. "
                        + "Configure app.notifications.sms.provider / app.notifications.sms.api-key "
                        + "to enable real delivery. To: {} - Message: {}", toAddress, body);
        return NotificationSendResult.loggedOnly();
    }
}
