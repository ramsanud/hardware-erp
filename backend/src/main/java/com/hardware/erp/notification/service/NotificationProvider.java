package com.hardware.erp.notification.service;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;

import java.util.Set;

/**
 * One implementation per outbound channel, or - for channels that are
 * presently a logging stub - one implementation covering several. Wiring in
 * a real SMS/WhatsApp provider later means writing one new class that
 * implements this interface and declares which channels it handles;
 * NotificationServiceImpl never changes, because it discovers providers by
 * scanning {@link #supportedChannels()} rather than switching on channel
 * itself.
 */
public interface NotificationProvider {

    Set<NotificationChannel> supportedChannels();

    /**
     * Sends (or, when unconfigured, logs) one message. {@code subject} is
     * null for channels that have no notion of one (SMS, WhatsApp).
     *
     * Returns {@link NotificationStatus#SENT} or
     * {@link NotificationStatus#LOGGED_ONLY} - never {@code FAILED}. A
     * provider that cannot deliver throws instead; the caller is the one
     * that records FAILED, so every attempt - success or failure - is
     * written to notification_log exactly once, in exactly one place.
     */
    NotificationStatus send(NotificationChannel channel, String toAddress, String subject, String body);
}
