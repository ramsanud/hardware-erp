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
 * The email channel over Twilio SendGrid's v3 Mail Send API (CR-074), used
 * instead of {@link EmailNotificationProvider}'s SMTP when
 * {@code app.notifications.email.provider=sendgrid}.
 *
 * The two are mutually exclusive by {@code @ConditionalOnProperty} - exactly
 * one EMAIL provider bean is ever constructed - for the same reason
 * {@link SmsNotificationProvider} stayed a single bean: two providers claiming
 * one {@link NotificationChannel} collide silently in NotificationServiceImpl's
 * channel map. It is the pattern the AI ChatCompletionClient beans already
 * use, not a new one invented here.
 *
 * <p>Why HTTP rather than SendGrid's SMTP relay, which would have needed no
 * code at all: an SMTP send returns nothing identifying, so every email would
 * keep writing a null {@code notification_log.provider_message_id}. The v3 API
 * returns SendGrid's own message id in {@code X-Message-Id}, which is what
 * makes an "it never arrived" report answerable - and it is the same reason
 * WhatsApp and Twilio sends are worth recording an id for.
 *
 * <p>SendGrid answers a successful send with <b>202 Accepted and an empty
 * body</b> - accepted for delivery, not delivered. That is the same meaning
 * {@link com.hardware.erp.notification.entity.NotificationStatus#SENT} already
 * carries for Meta's Cloud API, so no new status was needed.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.notifications.email", name = "provider", havingValue = "sendgrid")
public class SendGridEmailProvider implements NotificationProvider, EmailTransport {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final SendGridProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public SendGridEmailProvider(SendGridProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /** Package-private seam so tests drive the real request/response handling without a live SendGrid account. */
    SendGridEmailProvider(SendGridProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
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
        return "No SENDGRID_API_KEY / SENDGRID_FROM_EMAIL is set, so nothing was sent. "
                + "Set both - the from address must be a sender SendGrid has verified - then try again.";
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
            log.info("SendGrid is not configured - would have sent to {} - subject: {} - body: {}{}",
                    toAddress, subject, body,
                    attachment == null ? "" : " - attachment: " + attachment.describe());
            return NotificationSendResult.loggedOnly();
        }

        try {
            return callSendGrid(toAddress, subject, body, attachment);
        } catch (SendGridSendException ex) {
            throw ex;
        } catch (Exception ex) {
            // Same contract as every other provider: throw, and let the caller
            // be the single place that decides what a failure means.
            log.error("SendGrid send failed to {}", toAddress, ex);
            throw new SendGridSendException("Email could not be sent", ex);
        }
    }

    private NotificationSendResult callSendGrid(String toAddress, String subject, String body,
                                                NotificationAttachment attachment) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.apiBaseUrl() + "/mail/send"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildPayload(toAddress, subject, body, attachment), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new SendGridSendException("SendGrid returned HTTP %d: %s".formatted(
                    response.statusCode(), sendGridError(response.body())));
        }

        // 202 carries an empty body; the id lives in a header, and SendGrid
        // spells it X-Message-Id (HttpHeaders lookup is case-insensitive).
        String messageId = response.headers().firstValue("X-Message-Id").orElse(null);
        return NotificationSendResult.sent(messageId);
    }

    /**
     * Package-private so the payload shape is asserted directly. Built as maps
     * rather than records because SendGrid's schema is deeply nested and only
     * ever serialised - a record tree would be five private types for one
     * request body.
     */
    String buildPayload(String toAddress, String subject, String body, NotificationAttachment attachment) throws Exception {
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("email", properties.fromEmail());
        if (properties.fromName() != null && !properties.fromName().isBlank()) {
            from.put("name", properties.fromName());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("personalizations", List.of(Map.of("to", List.of(Map.of("email", toAddress)))));
        payload.put("from", from);
        // SendGrid rejects a missing or empty subject with a 400, where SMTP
        // would have delivered a blank one. Notification sends always set it;
        // this guards the channels that legitimately have none.
        payload.put("subject", subject == null || subject.isBlank() ? "(no subject)" : subject);
        payload.put("content", List.of(Map.of("type", "text/plain", "value", body)));

        if (attachment != null) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("content", Base64.getEncoder().encodeToString(attachment.content()));
            part.put("filename", attachment.filename());
            part.put("type", attachment.contentType());
            part.put("disposition", "attachment");
            List<Map<String, Object>> attachments = new ArrayList<>();
            attachments.add(part);
            payload.put("attachments", attachments);
        }

        return objectMapper.writeValueAsString(payload);
    }

    /** SendGrid reports problems as {"errors":[{"message":...,"field":...}]}; the first message is the actionable part. */
    private String sendGridError(String responseBody) {
        try {
            JsonNode errors = objectMapper.readTree(responseBody).path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                String message = errors.get(0).path("message").asText(null);
                if (message != null) {
                    return message;
                }
            }
        } catch (Exception ignored) {
            // Not JSON - the raw body is still the most useful thing to report.
        }
        return responseBody;
    }

    public static final class SendGridSendException extends RuntimeException {
        public SendGridSendException(String message) {
            super(message);
        }

        public SendGridSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
