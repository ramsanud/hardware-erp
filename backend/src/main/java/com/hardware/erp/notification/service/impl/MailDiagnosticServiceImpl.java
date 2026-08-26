package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.dto.MailDiagnosticResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.MailDiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Proves whether SMTP actually works, before anything important depends on it.
 *
 * Email OTP is the immediate reason this exists: switching login to require a
 * mailed code while the mail password is wrong locks every user out of their
 * own account, and the failure is invisible until someone tries to sign in.
 * The owner needs a way to confirm a real message arrives first.
 *
 * Synchronous on purpose, unlike SmtpMailService's @Async send - the whole
 * point is that the caller waits for the real answer. Never throws: a broken
 * mail server is the expected input here, not an exceptional one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailDiagnosticServiceImpl implements MailDiagnosticService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public MailDiagnosticResponse sendTestEmail(String toEmail) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return new MailDiagnosticResponse(
                    NotificationStatus.LOGGED_ONLY, null, toEmail,
                    "No MAIL_USER is set, so nothing was sent. Set MAIL_USER and MAIL_PASSWORD, then try again.");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Hardware ERP test email");
            message.setText("""
                    This is a test message from your Hardware ERP.

                    If you are reading it, outgoing email is working correctly and
                    features that depend on it - password reset links, invoice
                    sharing, and login codes - can be relied on.
                    """);
            mailSender.send(message);
            return new MailDiagnosticResponse(
                    NotificationStatus.SENT, fromAddress, toEmail,
                    "Accepted by the mail server. Check the inbox, and the spam folder.");
        } catch (Exception ex) {
            // The provider's own rejection text is the useful part - Gmail's
            // "535-5.7.8 Username and Password not accepted" tells the owner
            // exactly what to fix, where "send failed" tells them nothing.
            log.warn("Test email to {} failed", toEmail, ex);
            return new MailDiagnosticResponse(
                    NotificationStatus.FAILED, fromAddress, toEmail, rootMessage(ex));
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
