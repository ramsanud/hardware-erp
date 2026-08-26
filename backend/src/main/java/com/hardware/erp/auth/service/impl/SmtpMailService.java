package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async because SMTP latency must not sit inside the forgot-password request -
 * a slow mail server would otherwise make response time reveal whether the
 * account exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.mail.log-links-when-unconfigured:false}")
    private boolean logLinksWhenUnconfigured;

    @Override
    @Async("taskExecutor")
    public void sendPasswordResetLink(String toEmail, String recipientName, String resetUrl) {
        if (fromAddress == null || fromAddress.isBlank()) {
            // The link is a working credential. It is printed only when the dev
            // profile explicitly opts in, never in production.
            if (logLinksWhenUnconfigured) {
                log.warn("Mail not configured. Reset link for {}: {}", toEmail, resetUrl);
            } else {
                log.warn("Mail not configured; password reset link for user was not sent.");
            }
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Reset your Hardware ERP password");
            message.setText("""
                    Hello %s,

                    We received a request to reset your password. Open the link
                    below to set a new one. It expires in 30 minutes and can be
                    used only once.

                    %s

                    If you did not request this, ignore this email. Your password
                    has not been changed.
                    """.formatted(recipientName, resetUrl));
            mailSender.send(message);
        } catch (Exception ex) {
            // Never log the URL here - it would put a live credential in the logs.
            log.error("Failed to send password reset mail", ex);
        }
    }
}
