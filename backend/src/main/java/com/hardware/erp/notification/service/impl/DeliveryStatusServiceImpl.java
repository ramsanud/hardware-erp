package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationLog;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.repository.NotificationLogRepository;
import com.hardware.erp.notification.service.DeliveryStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * CR-085. The forward-progress rule moved here from WhatsAppWebhookController
 * unchanged, so the Meta path behaves exactly as before and the Twilio and
 * SendGrid paths get the same rule rather than a second copy of it.
 */
@Service
@RequiredArgsConstructor
public class DeliveryStatusServiceImpl implements DeliveryStatusService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    @Transactional
    public void apply(Long tenantId, String providerMessageId, NotificationStatus incoming) {
        advance(notificationLogRepository.findByTenantIdAndProviderMessageId(tenantId, providerMessageId), incoming);
    }

    @Override
    @Transactional
    public void apply(NotificationChannel channel, String providerMessageId, NotificationStatus incoming) {
        advance(notificationLogRepository.findByChannelAndProviderMessageId(channel, providerMessageId), incoming);
    }

    private void advance(Optional<NotificationLog> row, NotificationStatus incoming) {
        if (incoming == null) {
            return;
        }
        row.ifPresent(logRow -> {
            if (isForwardProgress(logRow.getStatus(), incoming)) {
                logRow.setStatus(incoming);
                notificationLogRepository.save(logRow);
            }
        });
    }

    /** A late "delivered" after "read" must not regress the row; a "failed" is only believable for a message that was merely sent. */
    static boolean isForwardProgress(NotificationStatus current, NotificationStatus incoming) {
        if (incoming == NotificationStatus.FAILED) {
            return current == NotificationStatus.SENT;
        }
        return rank(incoming) > rank(current);
    }

    private static int rank(NotificationStatus status) {
        return switch (status) {
            case LOGGED_ONLY, FAILED, QUOTA_EXCEEDED -> -1;
            case SENT -> 0;
            case DELIVERED -> 1;
            case READ -> 2;
        };
    }
}
