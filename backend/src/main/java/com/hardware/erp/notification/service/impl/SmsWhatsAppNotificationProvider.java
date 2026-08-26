package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * There is no SMS or WhatsApp provider account/API key available yet, so
 * this is a logging stub for both channels - not a fake "sent" response.
 * Every call is logged at INFO with the full intended message and returns
 * normally; callers (NotificationServiceImpl) never have to special-case
 * "SMS isn't configured yet".
 *
 * One class handles both channels because, until a real provider exists,
 * their behaviour is identical - only the log line's wording differs. When
 * a real provider is wired in, it will likely be one class per channel
 * again (a WhatsApp Business API account and an SMS gateway account are
 * rarely the same integration); at that point this stub can either be
 * deleted or split, and either way NotificationServiceImpl needs no change
 * because it routes purely off {@link #supportedChannels()}.
 */
@Slf4j
@Component
public class SmsWhatsAppNotificationProvider implements NotificationProvider {

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.SMS, NotificationChannel.WHATSAPP);
    }

    @Override
    public NotificationStatus send(NotificationChannel channel, String toAddress, String subject, String body) {
        String channelName = channel.name().toLowerCase(Locale.ROOT);
        log.info("{} provider not configured - message logged instead of sent. "
                        + "Configure app.notifications.{}.provider / app.notifications.{}.api-key "
                        + "to enable real delivery. To: {} - Message: {}",
                channel, channelName, channelName, toAddress, body);
        return NotificationStatus.LOGGED_ONLY;
    }
}
