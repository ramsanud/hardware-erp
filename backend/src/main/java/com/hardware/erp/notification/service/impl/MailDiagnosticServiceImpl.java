package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.dto.MailDiagnosticResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.MailDiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Proves whether outgoing email actually works, before anything important
 * depends on it.
 *
 * Email OTP is the immediate reason this exists: switching login to require a
 * mailed code while the mail password is wrong locks every user out of their
 * own account, and the failure is invisible until someone tries to sign in.
 * The owner needs a way to confirm a real message arrives first.
 *
 * Since CR-074 it tests through {@link EmailTransport}, so it exercises
 * whichever provider is actually configured - SMTP or SendGrid. Testing SMTP
 * on a SendGrid deployment would be worse than having no button: it would
 * report a problem that is not there, or a success that proves nothing about
 * the path real mail takes.
 *
 * Synchronous on purpose, unlike the @Async password-reset send - the whole
 * point is that the caller waits for the real answer. Never throws: a broken
 * mail server is the expected input here, not an exceptional one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailDiagnosticServiceImpl implements MailDiagnosticService {

    private final EmailTransport emailTransport;

    @Override
    public MailDiagnosticResponse sendTestEmail(String toEmail) {
        if (!emailTransport.isConfigured()) {
            return new MailDiagnosticResponse(
                    NotificationStatus.LOGGED_ONLY, null, toEmail, emailTransport.unconfiguredHint());
        }
        try {
            emailTransport.sendEmail(toEmail, "Hardware ERP test email", """
                    This is a test message from your Hardware ERP.

                    If you are reading it, outgoing email is working correctly and
                    features that depend on it - password reset links, invoice
                    sharing, and login codes - can be relied on.
                    """, null);
            return new MailDiagnosticResponse(
                    NotificationStatus.SENT, emailTransport.senderAddress(), toEmail,
                    "Accepted by the mail server. Check the inbox, and the spam folder.");
        } catch (Exception ex) {
            // The provider's own rejection text is the useful part - Gmail's
            // "535-5.7.8 Username and Password not accepted", or SendGrid's
            // "The from address does not match a verified Sender Identity",
            // tells the owner exactly what to fix where "send failed" tells
            // them nothing.
            log.warn("Test email to {} failed", toEmail, ex);
            return new MailDiagnosticResponse(
                    NotificationStatus.FAILED, emailTransport.senderAddress(), toEmail, rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message.trim();
    }
}
