package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.TenantWhatsAppConnection;
import com.hardware.erp.notification.entity.WhatsAppConnectionStatus;
import com.hardware.erp.notification.repository.TenantWhatsAppConnectionRepository;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * CR-056 - rebuilt tenant-scoped (previously one shared app-level
 * WHATSAPP_ACCESS_TOKEN/WHATSAPP_PHONE_NUMBER_ID for the whole
 * application, which is exactly the architecture this CR replaces - see
 * TenantWhatsAppConnection). Every send resolves the calling tenant's own
 * connection row and sends through Meta's Cloud API using THAT tenant's
 * own access token and phone number id - never a shared identity, and
 * never a value trusted from the caller (tenantId is always the invoice/
 * customer/tenant's own id, resolved server-side before this is called -
 * see NotificationServiceImpl).
 *
 * Graceful degradation, unchanged in spirit from before this CR: no
 * connected WhatsApp Business account for this tenant means "log instead
 * of send", not a thrown error - a shop that has never connected WhatsApp
 * keeps working exactly as if this feature did not exist. The one
 * deliberate exception is NotificationServiceImpl.sendTestWhatsApp(),
 * which checks isConfigured() itself first and throws before ever calling
 * send() - a "Test WhatsApp" button reporting success without sending
 * anything would be worse than not having the button.
 *
 * <p><b>A real limitation, stated here rather than hidden</b>: this sends
 * {@code type: "text"} messages. Meta only accepts free-form text for a
 * business-initiated conversation within 24 hours of the customer's own
 * last message; a reminder sent outside that window - which is most of
 * what this feature is for (a payment-due nudge days after an invoice) -
 * requires a pre-approved {@code type: "template"} message instead. Template
 * registration needs a real Meta Business Manager account per tenant,
 * which this environment cannot create by writing code - see the spec's
 * own Message Templates section, deliberately not built in this CR for the
 * same reason. Expect Meta to accept this call inside a live customer
 * session and reject it outside one - that is Meta's policy, not a bug
 * here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppBusinessProvider implements NotificationProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final TenantWhatsAppConnectionRepository connectionRepository;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.WHATSAPP);
    }

    /** Used by NotificationServiceImpl.sendTestWhatsApp() to fail fast instead of returning a fake LOGGED_ONLY "success". */
    public boolean isConfigured(Long tenantId) {
        return connectionRepository.findByTenantId(tenantId)
                .map(TenantWhatsAppConnection::isConnected)
                .orElse(false);
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body) {
        TenantWhatsAppConnection connection = connectionRepository.findByTenantId(tenantId).orElse(null);
        if (connection == null || !connection.isConnected()) {
            log.info("No connected WhatsApp Business account for tenant {} - message logged instead of sent. "
                    + "To: {} - Message: {}", tenantId, toAddress, body);
            return NotificationSendResult.loggedOnly();
        }

        try {
            return callMetaCloudApi(connection, toAddress, body);
        } catch (WhatsAppSendException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("WhatsApp send failed to {} for tenant {}", toAddress, tenantId, ex);
            throw new WhatsAppSendException("WhatsApp message could not be sent", ex);
        }
    }

    private NotificationSendResult callMetaCloudApi(TenantWhatsAppConnection connection, String toAddress, String body) throws Exception {
        String url = properties.apiBaseUrl() + "/" + connection.getPhoneNumberId() + "/messages";
        String payload = objectMapper.writeValueAsString(new MetaTextMessage(
                "whatsapp", toIndianE164(toAddress), "text", new MetaTextBody(body)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + connection.getAccessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            // The stored token no longer works (revoked/expired at Meta's end,
            // outside anything this app did) - mark it so the Settings page
            // shows "needs attention" instead of silently failing forever on
            // every future send. Does not clear the token: the owner may just
            // need to reconnect with a refreshed one, not re-enter everything.
            connection.setConnectionStatus(WhatsAppConnectionStatus.NEEDS_ATTENTION);
            connectionRepository.save(connection);
            throw new WhatsAppSendException(
                    "WhatsApp connection needs attention - reconnect your WhatsApp Business account.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new WhatsAppSendException(
                    "WhatsApp API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode messages = root.path("messages");
        String messageId = messages.isArray() && !messages.isEmpty()
                ? messages.get(0).path("id").asText(null) : null;
        return NotificationSendResult.sent(messageId);
    }

    /**
     * Indian mobile numbers are stored as bare 10 digits throughout this
     * app (see the {@code ^[6-9]\d{9}$} validation on Customer/Supplier/
     * User); Meta's Cloud API needs E.164 without the leading '+'. Only
     * India is handled - this app's own customer/supplier phone
     * validation is India-only today, so a non-10-digit value here would
     * already indicate a data problem elsewhere, not a real second country
     * to support.
     */
    private String toIndianE164(String mobileNo) {
        String digitsOnly = mobileNo.replaceAll("\\D", "");
        return digitsOnly.length() == 10 ? "91" + digitsOnly : digitsOnly;
    }

    private record MetaTextMessage(String messaging_product, String to, String type, MetaTextBody text) {}

    private record MetaTextBody(String body) {}

    public static final class WhatsAppSendException extends RuntimeException {
        public WhatsAppSendException(String message) {
            super(message);
        }
        public WhatsAppSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
