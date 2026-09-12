package com.hardware.erp.notification.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-074 - Twilio SendGrid, the email half of the same change.
 *
 * fromEmail must be an address verified at SendGrid (Single Sender or an
 * authenticated domain); SendGrid rejects anything else with a 403 no matter
 * how valid the API key is. Kept separate from spring.mail.username on
 * purpose - a deployment may keep an SMTP account configured for other
 * reasons while sending through SendGrid, and silently borrowing the SMTP
 * username as the SendGrid sender would produce exactly that 403 with no
 * obvious cause.
 */
@ConfigurationProperties(prefix = "app.notifications.email.sendgrid")
public record SendGridProperties(
        String apiBaseUrl,
        String apiKey,
        String fromEmail,
        String fromName
) {

    public boolean isConfigured() {
        return notBlank(apiKey) && notBlank(fromEmail);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
