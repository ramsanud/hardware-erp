package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.entity.EmailOtpPurpose;
import com.hardware.erp.auth.service.OtpMailService;
import com.hardware.erp.notification.service.EmailTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * CR-078. Async for the same reason {@link PasswordResetMailService} is:
 * mail latency must not sit inside a request whose timing could otherwise
 * reveal whether an account exists.
 *
 * Goes through {@link EmailTransport}, so it sends over whichever of SMTP,
 * SendGrid or Resend the deployment chose (CR-074, CR-077) without knowing
 * which.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpMailServiceImpl implements OtpMailService {

    private final EmailTransport emailTransport;

    /** The same dev-only switch that prints reset links: a code is a credential and is never logged in production. */
    @Value("${app.mail.log-links-when-unconfigured:false}")
    private boolean logCodesWhenUnconfigured;

    @Override
    @Async("taskExecutor")
    public void sendCode(String toEmail, String recipientName, EmailOtpPurpose purpose, String code, int validMinutes) {
        if (!emailTransport.isConfigured()) {
            if (logCodesWhenUnconfigured) {
                log.warn("Mail not configured. {} code for {}: {}", purpose, toEmail, code);
            } else {
                log.warn("Mail not configured; {} code was not sent.", purpose);
            }
            return;
        }
        try {
            emailTransport.sendEmail(toEmail, subjectFor(purpose), bodyFor(purpose, recipientName, code, validMinutes), null);
        } catch (Exception ex) {
            // Never log the code here - it would put a live credential in the logs.
            log.error("Failed to send {} code by email", purpose, ex);
        }
    }

    private static String subjectFor(EmailOtpPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFY -> "Your Hardware ERP verification code";
            case LOGIN -> "Your Hardware ERP sign-in code";
            case PASSWORD_RESET -> "Your Hardware ERP password reset code";
            case STEP_UP -> "Confirm it's you - Hardware ERP";
        };
    }

    /**
     * Plain text on purpose, like every other mail this application sends:
     * it renders identically in every client, and a code is the one thing in
     * the message a person needs to read.
     */
    private static String bodyFor(EmailOtpPurpose purpose, String recipientName, String code, int validMinutes) {
        String greeting = recipientName == null || recipientName.isBlank() ? "Hello," : "Hello " + recipientName + ",";
        String reason = switch (purpose) {
            case EMAIL_VERIFY -> "Use this code to confirm your email address:";
            case LOGIN -> "Use this code to finish signing in:";
            case PASSWORD_RESET -> "Use this code to set a new password:";
            case STEP_UP -> "Use this code to confirm the change you are making:";
        };
        return """
                %s

                %s

                    %s

                It expires in %d minutes and works only once. Nobody from
                Hardware ERP will ever ask you for it.

                If you did not request this, ignore this email.
                """.formatted(greeting, reason, code, validMinutes);
    }
}
