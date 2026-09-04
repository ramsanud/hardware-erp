package com.hardware.erp.notification.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-056 - global, app-wide Meta Platform config only. There is
 * deliberately no accessToken/phoneNumberId here any more (that was the
 * single-shared-number design this CR replaced) - those now live per
 * tenant in tenant_whatsapp_connection, see TenantWhatsAppConnectionService.
 *
 * apiBaseUrl is the one thing every tenant's calls share (Meta's own Graph
 * API host + version). appSecret/webhookVerifyToken back the inbound
 * webhook handshake and signature check (WhatsAppWebhookController) - see
 * that class's own javadoc for the real limitation this carries: they
 * assume every tenant's WABA is connected through this app's own Meta Tech
 * Provider app, true once real Embedded Signup ships, not necessarily true
 * for a tenant who supplied a token minted through a different Meta app of
 * their own during phase 1's manual entry.
 */
@ConfigurationProperties(prefix = "app.notifications.whatsapp")
public record WhatsAppProperties(
        String apiBaseUrl,
        String appSecret,
        String webhookVerifyToken
) {
}
