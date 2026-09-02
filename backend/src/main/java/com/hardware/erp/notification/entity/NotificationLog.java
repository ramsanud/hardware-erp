package com.hardware.erp.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per outbound notification attempt (CR-027). Plain tenant_id
 * column rather than a @ManyToOne Tenant - like activity_log and
 * security_audit_log, this is an append-only audit trail, not a record
 * that ever needs to navigate back to its tenant through JPA.
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_log_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    /** Null for SMS/WhatsApp - those channels have no subject line. */
    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "related_entity_type", length = 60)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    /** The provider's own message id on a real send - null for LOGGED_ONLY/FAILED. Not yet reconciled against delivery-status webhooks. */
    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
