package com.hardware.erp.notification.repository;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Page<NotificationLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    /** CR-056 §13 - the Message History page's WhatsApp-only filter. */
    Page<NotificationLog> findByTenantIdAndChannelOrderByCreatedAtDesc(
            Long tenantId, NotificationChannel channel, Pageable pageable);

    /** Backs the once-a-day dedup on NotificationServiceImpl.notifyPaymentDue. */
    boolean existsByTenantIdAndChannelAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
            Long tenantId, NotificationChannel channel, String relatedEntityType, Long relatedEntityId,
            LocalDateTime after);

    /**
     * CR-056 - resolves an inbound Meta delivery-status webhook event back
     * to the row it belongs to. Scoped by tenantId (resolved from the
     * webhook's own phone_number_id, never from the payload directly) as
     * well as providerMessageId - a message id is never trusted alone to
     * cross a tenant boundary. See WhatsAppWebhookController.
     */
    java.util.Optional<NotificationLog> findByTenantIdAndProviderMessageId(Long tenantId, String providerMessageId);
}
