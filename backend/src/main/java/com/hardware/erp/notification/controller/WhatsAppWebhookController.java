package com.hardware.erp.notification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationLog;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.entity.TenantWhatsAppConnection;
import com.hardware.erp.notification.repository.NotificationLogRepository;
import com.hardware.erp.notification.repository.TenantWhatsAppConnectionRepository;
import com.hardware.erp.notification.service.impl.WhatsAppProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * CR-056 §14 - inbound Meta WhatsApp Cloud API webhook. One endpoint for
 * the whole application (Meta only supports one callback URL per app),
 * routed to the right tenant per-event by the phone_number_id each event
 * itself carries (unique across tenant_whatsapp_connection - V45), never
 * by anything the request declares about who it is for.
 *
 * <p><b>A real limitation, stated here rather than hidden</b>: the
 * X-Hub-Signature-256 check below is only meaningful if the tenant whose
 * phone_number_id an event names actually had their WhatsApp Business
 * Account connected through THIS application's own Meta Tech Provider
 * app (true once real Embedded Signup ships - see TenantWhatsAppConnectionService's
 * own note). Phase 1's manual credential entry lets a tenant paste a
 * token minted through a *different* Meta app of their own; if they also
 * point that different app's webhook subscription at this endpoint, the
 * signature here - computed with this app's own APP_SECRET - will not
 * match theirs, and the event is correctly rejected rather than silently
 * accepted unverified. That tenant's delivery-status updates simply do
 * not arrive until this app's own Meta Tech Provider app is what their
 * number is connected through.
 */
@Slf4j
@RestController
@RequestMapping("/v1/webhooks/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Business")
public class WhatsAppWebhookController {

    private final WhatsAppProperties properties;
    private final TenantWhatsAppConnectionRepository connectionRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    /** Meta's one-time subscription handshake - answer with the challenge only if our shared verify token matches. */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        boolean tokenConfigured = properties.webhookVerifyToken() != null && !properties.webhookVerifyToken().isBlank();
        if ("subscribe".equals(mode) && tokenConfigured
                && MessageDigest.isEqual(
                        verifyToken.getBytes(StandardCharsets.UTF_8),
                        properties.webhookVerifyToken().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader) {
        if (!signatureValid(rawBody, signatureHeader)) {
            log.warn("Rejected a WhatsApp webhook event - signature missing or did not match");
            return ResponseEntity.status(403).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Could not parse WhatsApp webhook body", ex);
            return ResponseEntity.ok().build();
        }

        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
                if (phoneNumberId == null) {
                    continue;
                }
                Optional<TenantWhatsAppConnection> connection = connectionRepository.findByPhoneNumberId(phoneNumberId);
                if (connection.isEmpty()) {
                    // Not a phone number id this application knows about - never
                    // guess which tenant it might be, just drop the event.
                    log.warn("WhatsApp webhook event for an unknown phone_number_id - dropped");
                    continue;
                }
                Long tenantId = connection.get().getTenant().getId();
                for (JsonNode status : value.path("statuses")) {
                    applyStatus(tenantId, status);
                }
            }
        }
        // Meta expects a fast 200 regardless of whether an individual status
        // line matched a known message - it retries on anything else.
        return ResponseEntity.ok().build();
    }

    /**
     * Idempotent and retry-safe: Meta redelivers events, and this only ever
     * advances a row forward through SENT -> DELIVERED -> READ, or to
     * FAILED - it never regresses a later status back to an earlier one on
     * a re-delivered or out-of-order event.
     */
    private void applyStatus(Long tenantId, JsonNode statusNode) {
        String providerMessageId = statusNode.path("id").asText(null);
        String metaStatus = statusNode.path("status").asText(null);
        if (providerMessageId == null || metaStatus == null) {
            return;
        }
        NotificationStatus newStatus = switch (metaStatus) {
            case "delivered" -> NotificationStatus.DELIVERED;
            case "read" -> NotificationStatus.READ;
            case "failed" -> NotificationStatus.FAILED;
            default -> null; // "sent" is already how the row was created - nothing to advance to.
        };
        if (newStatus == null) {
            return;
        }

        notificationLogRepository.findByTenantIdAndProviderMessageId(tenantId, providerMessageId).ifPresent(logRow -> {
            if (isForwardProgress(logRow.getStatus(), newStatus)) {
                logRow.setStatus(newStatus);
                notificationLogRepository.save(logRow);
            }
        });
    }

    private boolean isForwardProgress(NotificationStatus current, NotificationStatus incoming) {
        if (incoming == NotificationStatus.FAILED) {
            return current == NotificationStatus.SENT;
        }
        int currentRank = rank(current);
        int incomingRank = rank(incoming);
        return incomingRank > currentRank;
    }

    private int rank(NotificationStatus status) {
        return switch (status) {
            case LOGGED_ONLY, FAILED -> -1;
            case SENT -> 0;
            case DELIVERED -> 1;
            case READ -> 2;
        };
    }

    private boolean signatureValid(String rawBody, String signatureHeader) {
        if (properties.appSecret() == null || properties.appSecret().isBlank()) {
            // Not configured in this environment - see this class's own
            // javadoc limitation. Refusing every event outright (rather
            // than accepting unverified ones) is the safer failure mode.
            log.warn("WHATSAPP_APP_SECRET is not configured - rejecting all inbound webhook events");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            String providedHex = signatureHeader.substring("sha256=".length());
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8), providedHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Could not verify WhatsApp webhook signature", ex);
            return false;
        }
    }
}
