package com.hardware.erp.notification.entity;

/** Must stay in step with ck_tenant_whatsapp_connection_status in V45__tenant_whatsapp_connection.sql. */
public enum WhatsAppConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    /** The stored token was rejected by Meta on a real send/verify attempt - the owner must reconnect. */
    NEEDS_ATTENTION
}
