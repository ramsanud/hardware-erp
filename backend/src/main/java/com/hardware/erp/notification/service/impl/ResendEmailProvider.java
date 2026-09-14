package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The email channel over Resend's Emails API (CR-077), used instead of SMTP
 * or SendGrid when {@code app.notifications.email.provider=resend}.
 *
 * Mutually exclusive with the other two by {@code @ConditionalOnProperty},
 * for the reason {@link SendGridEmailProvider} records: two beans claiming
 * the EMAIL channel collide silently in NotificationServiceImpl's channel
 * map. Adding a third value to the same switch is the whole integration -
 * password reset, sign-in codes, invoice PDFs and the Settings test button
 * all reach this class through {@link EmailTransport} without knowing it
 * exists.
 *
 * <p>Resend answers a successful send with <b>200 and {@code {"id": ...}}</b>
 * - accepted for delivery, not delivered - so the id goes into
 * {@code notification_log.provider_message_id} with the same SENT meaning
 * SendGrid's X-Message-Id carries.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.notifications.email", name = "provider", havingValue = "resend")
public class ResendEmailProvider implements NotificationProvider, EmailTransport {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ResendProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ResendEmailProvider(ResendProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /** Package-private seam so tests drive the real request/response handling without a live Resend account. */
    ResendEmailProvider(ResendProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.EMAIL);
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String senderAddress() {
        return properties.isConfigured() ? properties.fromEmail() : null;
    }

    @Override
    public String unconfiguredHint() {
        return "No RESEND_API_KEY / RESEND_FROM_EMAIL is set, so nothing was sent. "
                + "Set both - the from address must be on a domain verified at Resend - then try again.";
    }

    @Override
    public NotificationSendResult sendEmail(String toAddress, String subject, String body, NotificationAttachment attachment) {
        return send(null, NotificationChannel.EMAIL, toAddress, subject, body, attachment);
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body) {
        return send(tenantId, channel, toAddress, subject, body, null);
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress,
                                       String subject, String body, NotificationAttachment attachment) {
        if (!properties.isConfigured()) {
            log.info("Resend is not configured - would have sent to {} - subject: {} - body: {}{}",
                    toAddress, subject, body,
                    attachment == null ? "" : " - attachment: " + attachment.describe());
            return NotificationSendResult.loggedOnly();
        }

        try {
            return callResend(toAddress, subject, body, attachment);
        } catch (ResendSendException ex) {
            throw ex;
        } catch (Exception ex) {
            // Same contract as every other provider: throw, and let the caller
            // be the single place that decides what a failure means.
            log.error("Resend send failed to {}", toAddress, ex);
            throw new ResendSendException("Email could not be sent", ex);
        }
    }

    private NotificationSendResult callResend(String toAddress, String subject, String body,
                                              NotificationAttachment attachment) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.apiBaseUrl() + "/emails"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildPayload(toAddress, subject, body, attachment), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new ResendSendException("Resend returned HTTP %d: %s".formatted(
                    response.statusCode(), resendError(response.body())));
        }

        String messageId = objectMapper.readTree(response.body()).path("id").asText(null);
        return NotificationSendResult.sent(messageId);
    }

    /**
     * Package-private so the payload shape is asserted directly. Resend's
     * schema is flat, but the sender is one RFC 5322 string ("Name <addr>")
     * rather than an object, and that is the detail worth pinning down.
     */
    String buildPayload(String toAddress, String subject, String body, NotificationAttachment attachment) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromHeader());
        payload.put("to", List.of(toAddress));
        // Resend rejects a missing subject with a 422; SMTP would have sent
        // a blank one. Notification sends always set it; this guards the
        // channels that legitimately have none.
        payload.put("subject", subject == null || subject.isBlank() ? "(no subject)" : subject);
        payload.put("text", body);

        if (attachment != null) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("filename", attachment.filename());
            part.put("content", Base64.getEncoder().encodeToString(attachment.content()));
            part.put("content_type", attachment.contentType());
            List<Map<String, Object>> attachments = new ArrayList<>();
            attachments.add(part);
            payload.put("attachments", attachments);
        }

        return objectMapper.writeValueAsString(payload);
    }

    private String fromHeader() {
        String name = properties.fromName();
        if (name == null || name.isBlank()) {
            return properties.fromEmail();
        }
        return "%s <%s>".formatted(name, properties.fromEmail());
    }

    /** Resend reports problems as {"statusCode":..,"name":..,"message":..}; the message is the actionable part. */
    private String resendError(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            String message = node.path("message").asText(null);
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // Not JSON - the raw body is still the most useful thing to report.
        }
        return responseBody;
    }

    public static final class ResendSendException extends RuntimeException {
        public ResendSendException(String message) {
            super(message);
        }

        public ResendSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
