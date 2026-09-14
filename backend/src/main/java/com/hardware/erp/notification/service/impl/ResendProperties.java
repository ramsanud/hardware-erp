package com.hardware.erp.notification.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CR-077 - Resend, the third email backend beside SMTP and SendGrid.
 *
 * Chosen for its free tier (3,000 emails a month, 100 a day at the time of
 * writing) - enough for a shop's password resets, sign-in codes and invoice
 * mail without a paid plan, which is the whole reason CR-077 exists: the
 * auth stack is being built on email because SMS is not free.
 *
 * fromEmail must be on a domain verified at Resend; the API answers 403 to
 * anything else. Resend's {@code onboarding@resend.dev} sender works with no
 * domain at all but only delivers to the account owner's own address, which
 * makes it fine for a first smoke test and useless for a real shop. Kept
 * separate from spring.mail.username for the same reason SendGrid's is.
 */
@ConfigurationProperties(prefix = "app.notifications.email.resend")
public record ResendProperties(
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
