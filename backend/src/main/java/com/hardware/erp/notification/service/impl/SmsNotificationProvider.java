package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.util.PhoneNumberNormalizer;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.service.NotificationProvider;
import com.hardware.erp.notification.service.NotificationSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The SMS channel, sent through Twilio Programmable Messaging (CR-074).
 * Until CR-074 this class was a logging stub - no SMS account existed.
 *
 * Kept as the one bean claiming {@link NotificationChannel#SMS} rather than
 * adding a second "Twilio" bean beside the stub: two providers claiming the
 * same channel silently collide in NotificationServiceImpl's channel map
 * (whichever Spring iterates last wins, with no error), which is why
 * {@link #supportedChannels()} narrowing to SMS alone was already load-bearing
 * before this change. Twilio is this class's backend, not a second provider.
 *
 * Graceful degradation is unchanged and deliberate: with no credentials set,
 * every call still logs the intended message and returns LOGGED_ONLY. A
 * deployment that has not bought a Twilio number keeps working exactly as it
 * did, and no caller has to special-case "SMS isn't configured yet".
 *
 * <p><b>A real limitation, stated rather than hidden</b>: India requires every
 * commercial SMS sender and template to be registered under TRAI's DLT regime
 * before a message reaches an Indian handset. Twilio accepts these API calls
 * and returns a message SID regardless; delivery is then refused downstream by
 * the operator when the sender id or content is not registered. That
 * registration is a business process with a real telecom operator - no code
 * here can satisfy it. Configure {@code messaging-service-sid} with a
 * DLT-registered Messaging Service for a live Indian deployment;
 * {@code from-number} is fine for testing and for non-Indian numbers.
 */
@Slf4j
@Component
public class SmsNotificationProvider implements NotificationProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final TwilioProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    /**
     * CR-085 - optional and setter-injected on purpose: the constructors are
     * called from three test classes, and a callback URL is capability the
     * send gains, not a new thing every caller must supply.
     */
    private NotificationWebhookProperties webhookProperties;

    @Autowired
    public SmsNotificationProvider(TwilioProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    /** Package-private seam so tests drive the real request/response handling without a live Twilio account. */
    SmsNotificationProvider(TwilioProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Autowired(required = false)
    public void setWebhookProperties(NotificationWebhookProperties webhookProperties) {
        this.webhookProperties = webhookProperties;
    }

    @Override
    public Set<NotificationChannel> supportedChannels() {
        return EnumSet.of(NotificationChannel.SMS);
    }

    /** Lets a caller tell "not set up" apart from "failed" - the same role isConfigured() plays for WhatsApp. */
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public NotificationSendResult send(Long tenantId, NotificationChannel channel, String toAddress, String subject, String body) {
        if (!properties.enabled()) {
            // CR-077: the paid channel is off unless a deployment opts in. Debug,
            // not info - with SMS deliberately off this fires on every invoice
            // and payment and is not news.
            log.debug("SMS is disabled (SMS_ENABLED=false) - logged instead of sent. To: {} - Message: {}",
                    toAddress, body);
            return NotificationSendResult.loggedOnly();
        }
        if (!properties.isConfigured()) {
            log.info("Twilio is not configured - SMS logged instead of sent. Set TWILIO_ACCOUNT_SID, "
                    + "TWILIO_AUTH_TOKEN and TWILIO_MESSAGING_SERVICE_SID (or TWILIO_FROM_NUMBER) to enable "
                    + "real delivery. To: {} - Message: {}", toAddress, body);
            return NotificationSendResult.loggedOnly();
        }

        try {
            return callTwilio(toAddress, body);
        } catch (SmsSendException ex) {
            throw ex;
        } catch (Exception ex) {
            // The interface contract: a provider that cannot deliver throws, and
            // the caller is the single place that records FAILED.
            log.error("Twilio SMS send failed to {}", toAddress, ex);
            throw new SmsSendException("SMS could not be sent", ex);
        }
    }

    private NotificationSendResult callTwilio(String toAddress, String body) throws Exception {
        String url = "%s/Accounts/%s/Messages.json".formatted(properties.apiBaseUrl(), properties.accountSid());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(toAddress, body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            // Twilio puts a numeric code and a documented message in the error
            // body; carrying it through is the difference between an owner
            // reading "21608 - unverified trial number" and reading "send failed".
            throw new SmsSendException("Twilio returned HTTP %d: %s".formatted(
                    response.statusCode(), twilioError(response.body())));
        }

        JsonNode root = objectMapper.readTree(response.body());
        // "sid" is Twilio's own message id, kept in notification_log.provider_message_id
        // for the delivery-status reconciliation that column was already shaped for.
        String messageSid = root.path("sid").asText(null);
        return NotificationSendResult.sent(messageSid);
    }

    /**
     * Twilio authenticates with HTTP Basic - account SID as the user, auth
     * token as the password - not a bearer token like Meta's Cloud API.
     */
    private String basicAuthHeader() {
        String credentials = properties.accountSid() + ":" + properties.authToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /** Package-private so a test asserts the exact wire format rather than guessing at it. */
    String formBody(String toAddress, String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("To", toE164(toAddress));
        // A Messaging Service wins when both are set: it is what carries the
        // DLT-registered sender id India requires, and a bare From alongside it
        // would override that pool for no benefit.
        if (properties.messagingServiceSid() != null && !properties.messagingServiceSid().isBlank()) {
            fields.put("MessagingServiceSid", properties.messagingServiceSid());
        } else {
            fields.put("From", properties.fromNumber());
        }
        fields.put("Body", body);
        // CR-085 - ask Twilio to report delivery to the status webhook, but only
        // when this deployment has a public address for it to call. Without
        // one the row stays SENT, exactly as before.
        if (webhookProperties != null && webhookProperties.callbacksEnabled()) {
            fields.put("StatusCallback", webhookProperties.twilioStatusCallbackUrl());
        }

        return fields.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /**
     * Twilio wants full E.164 including the '+'. Delegates to
     * {@link PhoneNumberNormalizer} since CR-080 (see that class for why the
     * copy this method used to be was worth removing); kept as a method so
     * the wire-format tests keep reading naturally.
     */
    String toE164(String mobileNo) {
        return PhoneNumberNormalizer.toE164(mobileNo);
    }

    private String twilioError(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("message").asText(null);
            int code = root.path("code").asInt(0);
            if (message != null) {
                return code == 0 ? message : code + " - " + message;
            }
        } catch (Exception ignored) {
            // Not JSON (a proxy's HTML error page, say) - the raw body is still
            // the most useful thing to report.
        }
        return responseBody;
    }

    public static final class SmsSendException extends RuntimeException {
        public SmsSendException(String message) {
            super(message);
        }

        public SmsSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
