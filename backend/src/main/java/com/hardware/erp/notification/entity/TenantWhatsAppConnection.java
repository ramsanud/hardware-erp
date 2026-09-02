package com.hardware.erp.notification.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CR-056 - one row per tenant, holding that tenant's own Meta WhatsApp
 * Cloud API connection. Never shared across tenants: every outbound send
 * and every inbound webhook event is resolved through this table, keyed
 * either by tenant_id (outbound) or phone_number_id (inbound - see
 * WhatsAppWebhookController), never by a value the client supplies.
 */
@Entity
@Table(name = "tenant_whatsapp_connection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantWhatsAppConnection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "whatsapp_connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "business_account_id", nullable = false, length = 50)
    private String businessAccountId;

    @Column(name = "phone_number_id", nullable = false, length = 50)
    private String phoneNumberId;

    @Column(name = "display_phone_number", nullable = false, length = 20)
    private String displayPhoneNumber;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    /** Encrypted at rest - see WhatsAppAccessTokenConverter. Never serialized to any DTO. */
    @Column(name = "access_token", length = 2000)
    @Convert(converter = WhatsAppAccessTokenConverter.class)
    private String accessToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20)
    @Builder.Default
    private WhatsAppConnectionStatus connectionStatus = WhatsAppConnectionStatus.CONNECTED;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    public boolean isConnected() {
        return connectionStatus == WhatsAppConnectionStatus.CONNECTED;
    }

    /** Last 2 digits only - "Phone" in the spec's own settings mockup shows the rest as X's. */
    public String maskedPhoneNumber() {
        if (displayPhoneNumber == null || displayPhoneNumber.length() < 2) {
            return "**";
        }
        String lastTwo = displayPhoneNumber.substring(displayPhoneNumber.length() - 2);
        return "*".repeat(Math.max(0, displayPhoneNumber.length() - 2)) + lastTwo;
    }
}
