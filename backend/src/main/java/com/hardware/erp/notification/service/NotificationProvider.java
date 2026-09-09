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
     * Returns {@link NotificationSendResult} carrying
     * {@link NotificationStatus#SENT} (with the provider's own message id)
     * or {@link NotificationStatus#LOGGED_ONLY} - never {@code FAILED}. A
     * provider that cannot deliver throws instead; the caller is the one
     * that records FAILED, so every attempt - success or failure - is
     * written to notification_log exactly once, in exactly one place.
     *
     * {@code tenantId} (CR-056) is who this message is sent as - required so
     * {@link com.hardware.erp.notification.service.impl.WhatsAppBusinessProvider}
     * can resolve which tenant's own WhatsApp Business connection to send
     * through. Every other provider ignores it; it is still on the shared
     * interface rather than a WhatsApp-only overload so NotificationServiceImpl
     * keeps discovering providers uniformly by channel, not by type.
     */
    NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body);

    /**
     * CR-073: the same send, with one optional file attached.
     *
     * A default that drops the attachment, rather than a sixth parameter on
     * the method above, precisely so the uniform discovery described in this
     * interface's own javadoc survives: SMS and WhatsApp have no notion of an
     * email attachment and are not made to pretend otherwise. Only
     * {@link com.hardware.erp.notification.service.impl.EmailNotificationProvider}
     * overrides it. A provider that inherits this default still delivers the
     * message - silently, and without the file, which is the correct outcome
     * for a channel that cannot carry one.
     */
    default NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress,
                                        String subject, String body, NotificationAttachment attachment) {
        return send(tenantId, channel, toAddress, subject, body);
    }
}
