package com.hardware.erp.notification.dto;

import com.hardware.erp.notification.entity.NotificationStatus;

/**
 * The honest result of trying to send one test email.
 *
 * `detail` carries the provider's own rejection text (e.g. Gmail's
 * "535-5.7.8 Username and Password not accepted") because that string is the
 * single most useful thing when SMTP is misconfigured - without it the owner
 * only learns "it did not work".
 */
public record MailDiagnosticResponse(
        NotificationStatus status,
        String fromAddress,
        String toAddress,
        String detail
) {}
