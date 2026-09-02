package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Wraps the same JavaMailSender / "unconfigured" detection SmtpMailService
 * already uses for password-reset mail (spring.mail.username blank = no
 * real SMTP account behind this deployment yet). Deliberately does not
 * reuse SmtpMailService itself - that class is scoped to the one
 * password-reset template and stays that way; this is the general-purpose
 * email channel for everything else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationProvider implements NotificationProvider {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.EMAIL);
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.info("Mail not configured - would have sent to {} - subject: {} - body: {}",
                    toAddress, subject, body);
            return NotificationSendResult.loggedOnly();
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        // SMTP itself never hands back a provider-side message id.
        return NotificationSendResult.sent(null);
    }
}
