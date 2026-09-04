package com.hardware.erp.notification.dto;

import com.hardware.erp.notification.entity.WhatsAppConnectionStatus;

import java.time.LocalDateTime;

/**
 * What the frontend is allowed to know about a tenant's WhatsApp
 * connection - never the access token (spec §5). connected=false with
 * every other field null is the "not connected yet" shape.
 */
public record WhatsAppConnectionResponse(
        boolean connected,
        WhatsAppConnectionStatus status,
        String businessName,
        String phoneNumberMasked,
        LocalDateTime connectedAt,
        LocalDateTime lastVerifiedAt
) {
    public static WhatsAppConnectionResponse notConnected() {
        return new WhatsAppConnectionResponse(false, WhatsAppConnectionStatus.DISCONNECTED, null, null, null, null);
    }
}
