package com.hardware.erp.notification.service;

import com.hardware.erp.notification.dto.WhatsAppConnectionRequest;
import com.hardware.erp.notification.dto.WhatsAppConnectionResponse;

/**
 * CR-056 - connect/disconnect/status for the calling tenant's own WhatsApp
 * Business connection. Every method resolves the tenant from
 * SecurityUtils.requireCurrentTenantId(), never from a request field -
 * there is deliberately no tenantId parameter anywhere on this interface.
 */
public interface TenantWhatsAppConnectionService {

    WhatsAppConnectionResponse getStatus();

    /** Verifies the credentials against Meta's own Graph API before saving anything. */
    WhatsAppConnectionResponse connect(WhatsAppConnectionRequest request);

    /** Keeps the row (business name, phone, history) but revokes the stored token and marks it disconnected. */
    WhatsAppConnectionResponse disconnect();
}
