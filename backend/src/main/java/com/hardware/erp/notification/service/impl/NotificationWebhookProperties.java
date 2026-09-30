package com.hardware.erp.notification.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-085 - what the app-wide providers need in order to tell us what became
 * of a message. Kept apart from TwilioProperties / SendGridProperties (their
 * records are constructed in a dozen tests) and from WhatsAppProperties (a
 * per-tenant channel with its own secret).
 *
 * publicBaseUrl is the address the provider reaches this application at -
 * "https://erp.example.in" - and is needed twice: Twilio is told where to
 * call back on every send, and Twilio's signature is computed over the exact
 * URL it called, so the check must reconstruct it. Blank means "no callbacks
 * asked for", which is how every deployment worked before CR-085.
 *
 * sendgridPublicKey is the base64 verification key SendGrid shows when the
 * Signed Event Webhook is enabled. Blank means every SendGrid event is
 * refused - the same fail-closed rule WhatsAppWebhookController applies when
 * its app secret is missing.
 */
@ConfigurationProperties(prefix = "app.notifications.webhooks")
public record NotificationWebhookProperties(
        String publicBaseUrl,
        String sendgridPublicKey
) {

    public boolean callbacksEnabled() {
        return notBlank(publicBaseUrl);
    }

    /** No trailing slash, so a path can be appended verbatim. */
    public String twilioStatusCallbackUrl() {
        return publicBaseUrl.replaceAll("/+$", "") + "/api/v1/webhooks/twilio/status";
    }

    public boolean sendgridVerificationConfigured() {
        return notBlank(sendgridPublicKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
