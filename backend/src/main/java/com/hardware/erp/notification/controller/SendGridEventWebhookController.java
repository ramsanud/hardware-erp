package com.hardware.erp.notification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.DeliveryStatusService;
import com.hardware.erp.notification.service.impl.NotificationWebhookProperties;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * CR-085 - SendGrid's Event Webhook for the email channel. SendGrid POSTs a
 * JSON array of events; each carries sg_message_id, which begins with the
 * X-Message-Id the send returned (SendGridEmailProvider records that as the
 * row's providerMessageId) followed by ".filter..." routing detail, so the
 * row is found by that prefix.
 *
 * <p>Authenticity is SendGrid's Signed Event Webhook: an ECDSA P-256
 * signature over timestamp + raw body, verified against the public key
 * SendGrid shows when signing is enabled. Every event is refused while the
 * key is unconfigured - fail closed, as the other webhooks do.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/v1/webhooks/sendgrid")
@RequiredArgsConstructor
public class SendGridEventWebhookController {

    private final NotificationWebhookProperties webhookProperties;
    private final DeliveryStatusService deliveryStatusService;
    private final ObjectMapper objectMapper;

    @PostMapping("/events")
    public ResponseEntity<Void> events(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Timestamp", required = false) String timestamp) {
        if (!signatureValid(rawBody, signature, timestamp)) {
            log.warn("Rejected a SendGrid event batch - signature missing or did not match");
            return ResponseEntity.status(403).build();
        }

        JsonNode events;
        try {
            events = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Could not parse SendGrid event body", ex);
            return ResponseEntity.ok().build();
        }
        for (JsonNode event : events) {
            String messageId = messageIdOf(event.path("sg_message_id").asText(null));
            NotificationStatus incoming = switch (event.path("event").asText("")) {
                case "delivered" -> NotificationStatus.DELIVERED;
                // Open tracking is the closest email has to "read"; it fires
                // only when the recipient's client loads images, so a READ
                // here is a lower bound, never a claim they did not.
                case "open" -> NotificationStatus.READ;
                case "bounce", "dropped" -> NotificationStatus.FAILED;
                default -> null;
            };
            if (messageId != null) {
                deliveryStatusService.apply(NotificationChannel.EMAIL, messageId, incoming);
            }
        }
        return ResponseEntity.ok().build();
    }

    /** "abc123.filterdrecv-xyz" -> "abc123", the X-Message-Id the send returned. */
    static String messageIdOf(String sgMessageId) {
        if (sgMessageId == null || sgMessageId.isBlank()) {
            return null;
        }
        int dot = sgMessageId.indexOf('.');
        return dot > 0 ? sgMessageId.substring(0, dot) : sgMessageId;
    }

    boolean signatureValid(String rawBody, String signature, String timestamp) {
        if (!webhookProperties.sendgridVerificationConfigured() || signature == null || timestamp == null) {
            return false;
        }
        try {
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(webhookProperties.sendgridPublicKey())));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update((timestamp + rawBody).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception ex) {
            log.warn("Could not verify a SendGrid signature", ex);
            return false;
        }
    }
}
