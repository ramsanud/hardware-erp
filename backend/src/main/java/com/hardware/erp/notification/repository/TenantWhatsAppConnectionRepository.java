package com.hardware.erp.notification.repository;

import com.hardware.erp.notification.entity.TenantWhatsAppConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantWhatsAppConnectionRepository extends JpaRepository<TenantWhatsAppConnection, Long> {

    Optional<TenantWhatsAppConnection> findByTenantId(Long tenantId);

    /**
     * The one lookup an inbound Meta webhook event is allowed to use to find
     * a tenant - phone_number_id is globally unique (V45), never guessed
     * from a client-controlled field. See WhatsAppWebhookController.
     */
    Optional<TenantWhatsAppConnection> findByPhoneNumberId(String phoneNumberId);
}
