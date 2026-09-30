package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.service.EmailOtpService;
import com.hardware.erp.auth.service.MailService;
import com.hardware.erp.notification.service.EmailTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async because mail latency must not sit inside the forgot-password request -
 * a slow mail server would otherwise make response time reveal whether the
 * account exists.
 *
 * Was SmtpMailService until CR-074, talking to JavaMailSender directly. It
 * now sends through {@link EmailTransport}, so a deployment that switched the
 * email channel to SendGrid keeps sending password-reset links; before this,
 * such a deployment would have looked healthy, sent invoice notifications, and
 * silently dropped every reset link, because this class had no SMTP account to
 * use. The rename follows: what it does is send the password-reset mail, and
 * SMTP is no longer necessarily how.
 *
 * CR-078 put a six-digit code in the same email as the link. One email, two
 * ways to use it: the code for a phone, where a tapped link opens the wrong
 * browser or none, and the link for a desktop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService implements MailService {

    private final EmailTransport emailTransport;

    @Value("${app.mail.log-links-when-unconfigured:false}")
    private boolean logLinksWhenUnconfigured;

    @Value("${app.password-reset.token-validity-minutes:30}")
    private int linkValidityMinutes;

    @Override
    @Async("taskExecutor")
    public void sendPasswordResetLink(String toEmail, String recipientName, String resetUrl, String code) {
        if (!emailTransport.isConfigured()) {
            // The link and the code are working credentials. Printed only when
            // the dev profile explicitly opts in, never in production.
            if (logLinksWhenUnconfigured) {
                log.warn("Mail not configured. Reset link for {}: {} (code: {})", toEmail, resetUrl, code);
            } else {
                log.warn("Mail not configured; password reset link for user was not sent.");
            }
            return;
        }
        try {
            String codeParagraph = code != null
                    ? """
                      Enter this code on the reset screen:

                          %s

                      It expires in %d minutes. Or open the link below - it works
                      for %d minutes.""".formatted(code, EmailOtpService.VALID_MINUTES, linkValidityMinutes)
                    : """
                      Enter the code we sent you a moment ago, or open the link
                      below - it works for %d minutes.""".formatted(linkValidityMinutes);

            emailTransport.sendEmail(toEmail, "Reset your Hardware ERP password", """
                    Hello %s,

                    We received a request to reset your password.

                    %s

                    %s

                    Either one can be used only once. If you did not request
                    this, ignore this email. Your password has not been changed.
                    """.formatted(recipientName, codeParagraph, resetUrl), null);
        } catch (Exception ex) {
            // Never log the URL or the code here - it would put a live credential in the logs.
            log.error("Failed to send password reset mail", ex);
        }
    }
}
