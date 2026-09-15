package com.hardware.erp.notification.controller;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.DeliveryStatusService;
import com.hardware.erp.notification.service.impl.NotificationWebhookProperties;
import com.hardware.erp.notification.service.impl.TwilioProperties;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * CR-085 - Twilio's status callback for the SMS channel. Twilio POSTs a form
 * here for every status change of a message we asked it to report on
 * (SmsNotificationProvider sets StatusCallback when a public base URL is
 * configured).
 *
 * <p>Authenticity is Twilio's own scheme, not a JWT of ours: X-Twilio-Signature
 * is base64(HMAC-SHA1(auth token, full URL + every POST field appended in
 * key order)). The URL must be exactly the one Twilio called, so it is
 * rebuilt from the configured public base URL rather than read from the
 * request, which a proxy may have rewritten. Every event is refused when
 * the token or the base URL is missing - the fail-closed rule the WhatsApp
 * webhook already follows.
 *
 * <p>Permitted without a session in SecurityConfig for the same reason the
 * Meta and Razorpay webhooks are.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/v1/webhooks/twilio")
@RequiredArgsConstructor
public class TwilioStatusWebhookController {

    private final TwilioProperties twilioProperties;
    private final NotificationWebhookProperties webhookProperties;
    private final DeliveryStatusService deliveryStatusService;

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(
            @RequestParam Map<String, String> form,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        if (!signatureValid(form, signature)) {
            log.warn("Rejected a Twilio status callback - signature missing or did not match");
            return ResponseEntity.status(403).build();
        }

        String sid = form.get("MessageSid");
        String twilioStatus = form.get("MessageStatus");
        if (sid == null || twilioStatus == null) {
            return ResponseEntity.ok().build();
        }
        // "queued", "sending" and "sent" are all the state the row was created
        // in; only what happens after the operator takes it is news.
        NotificationStatus incoming = switch (twilioStatus) {
            case "delivered" -> NotificationStatus.DELIVERED;
            case "read" -> NotificationStatus.READ;
            case "failed", "undelivered" -> NotificationStatus.FAILED;
            default -> null;
        };
        deliveryStatusService.apply(NotificationChannel.SMS, sid, incoming);
        return ResponseEntity.ok().build();
    }

    boolean signatureValid(Map<String, String> form, String signature) {
        String token = twilioProperties.authToken();
        if (token == null || token.isBlank() || !webhookProperties.callbacksEnabled() || signature == null) {
            return false;
        }
        try {
            StringBuilder payload = new StringBuilder(webhookProperties.twilioStatusCallbackUrl());
            new TreeMap<>(form).forEach((key, value) -> payload.append(key).append(value));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String expected = Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("Could not verify a Twilio signature", ex);
            return false;
        }
    }
}
