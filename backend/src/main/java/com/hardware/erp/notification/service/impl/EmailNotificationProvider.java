package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * The email channel over SMTP, and the default one - a deployment that sets
 * nothing keeps the behaviour it has always had. "Unconfigured" is still a
 * blank spring.mail.username, meaning no real SMTP account sits behind this
 * deployment, in which case every send is logged rather than delivered.
 *
 * CR-074 made two things true of this class that were not before. It is now
 * {@code @ConditionalOnProperty} on {@code app.notifications.email.provider},
 * so it and {@link SendGridEmailProvider} are mutually exclusive - two beans
 * claiming {@link NotificationChannel#EMAIL} would collide silently in
 * NotificationServiceImpl's channel map. And it now implements
 * {@link EmailTransport}, which is how password-reset mail and the Settings
 * mail diagnostic reach whichever provider is active instead of each reaching
 * for JavaMailSender itself - see that interface for why that mattered.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.notifications.email", name = "provider",
        havingValue = "smtp", matchIfMissing = true)
@RequiredArgsConstructor
public class EmailNotificationProvider implements NotificationProvider, EmailTransport {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.EMAIL);
    }

    @Override
    public boolean isConfigured() {
        return fromAddress != null && !fromAddress.isBlank();
    }

    @Override
    public String senderAddress() {
        return isConfigured() ? fromAddress : null;
    }

    @Override
    public String unconfiguredHint() {
        return "No MAIL_USER is set, so nothing was sent. Set MAIL_USER and MAIL_PASSWORD, then try again.";
    }

    @Override
    public NotificationSendResult sendEmail(String toAddress, String subject, String body, NotificationAttachment attachment) {
        return send(null, NotificationChannel.EMAIL, toAddress, subject, body, attachment);
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body) {
        return send(tenantId, channel, toAddress, subject, body, null);
    }

    /**
     * CR-073. With no attachment this stays on the SimpleMailMessage path it
     * has always used; a plain text mail does not become a MIME multipart
     * just because the method now can produce one.
     */
    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress,
                                       String subject, String body, NotificationAttachment attachment) {
        if (!isConfigured()) {
            log.info("Mail not configured - would have sent to {} - subject: {} - body: {}{}",
                    toAddress, subject, body,
                    attachment == null ? "" : " - attachment: " + attachment.describe());
            return NotificationSendResult.loggedOnly();
        }

        if (attachment == null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            // SMTP itself never hands back a provider-side message id.
            return NotificationSendResult.sent(null);
        }

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // true = multipart, which is what makes room for the file at all.
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachment.filename(),
                    new ByteArrayResource(attachment.content()), attachment.contentType());
            mailSender.send(mime);
            return NotificationSendResult.sent(null);
        } catch (MessagingException ex) {
            // The interface contract: a provider that cannot deliver throws, and
            // the caller is the single place that records FAILED.
            throw new IllegalStateException("Could not build the support email", ex);
        }
    }
}
