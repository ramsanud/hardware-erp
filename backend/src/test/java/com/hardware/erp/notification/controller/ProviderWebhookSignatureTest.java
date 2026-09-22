package com.hardware.erp.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.DeliveryStatusService;
import com.hardware.erp.notification.service.impl.NotificationWebhookProperties;
import com.hardware.erp.notification.service.impl.TwilioProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CR-085. Both provider callbacks are signed by the provider, not by us, so
 * the thing worth pinning is that a correct signature is accepted, a wrong
 * one refused, and an unconfigured secret refuses everything - the
 * fail-closed rule the WhatsApp webhook set.
 */
@ExtendWith(MockitoExtension.class)
class ProviderWebhookSignatureTest {

    private static final String BASE_URL = "https://erp.example.in";
    private static final String TOKEN = "twilio-auth-token";

    @Mock private DeliveryStatusService deliveryStatusService;

    private TwilioStatusWebhookController twilio(String baseUrl, String token) {
        return new TwilioStatusWebhookController(
                new TwilioProperties(true, "https://api.twilio.com/2010-04-01", "ACx", token, "+15550001111", null),
                new NotificationWebhookProperties(baseUrl, null),
                deliveryStatusService);
    }

    private static Map<String, String> twilioForm(String status) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("MessageSid", "SM123");
        form.put("MessageStatus", status);
        form.put("To", "+919876543210");
        return form;
    }

    /** Twilio's documented scheme: base64(HMAC-SHA1(token, url + params sorted by key, key then value)). */
    private static String twilioSignature(String url, Map<String, String> form, String token) throws Exception {
        StringBuilder payload = new StringBuilder(url);
        form.keySet().stream().sorted().forEach(key -> payload.append(key).append(form.get(key)));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Twilio: a correctly signed 'delivered' advances the SMS row; the URL signed is the configured public one")
    void twilioAcceptsAValidSignature() throws Exception {
        Map<String, String> form = twilioForm("delivered");
        String signature = twilioSignature(BASE_URL + "/api/v1/webhooks/twilio/status", form, TOKEN);

        assertThat(twilio(BASE_URL, TOKEN).status(form, signature).getStatusCode().value()).isEqualTo(200);
        verify(deliveryStatusService).apply(NotificationChannel.SMS, "SM123", NotificationStatus.DELIVERED);
    }

    @Test
    @DisplayName("Twilio: a trailing slash on the base URL does not change the signed URL")
    void twilioNormalisesTheBaseUrl() throws Exception {
        Map<String, String> form = twilioForm("undelivered");
        String signature = twilioSignature(BASE_URL + "/api/v1/webhooks/twilio/status", form, TOKEN);

        assertThat(twilio(BASE_URL + "/", TOKEN).status(form, signature).getStatusCode().value()).isEqualTo(200);
        verify(deliveryStatusService).apply(NotificationChannel.SMS, "SM123", NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("Twilio: a wrong signature, a missing header, or no public base URL is 403 and touches nothing")
    void twilioRefusesTheRest() throws Exception {
        Map<String, String> form = twilioForm("delivered");
        String forged = twilioSignature(BASE_URL + "/api/v1/webhooks/twilio/status", form, "another-token");

        assertThat(twilio(BASE_URL, TOKEN).status(form, forged).getStatusCode().value()).isEqualTo(403);
        assertThat(twilio(BASE_URL, TOKEN).status(form, null).getStatusCode().value()).isEqualTo(403);
        assertThat(twilio("", TOKEN).status(form,
                twilioSignature("/api/v1/webhooks/twilio/status", form, TOKEN)).getStatusCode().value()).isEqualTo(403);
        verify(deliveryStatusService, never()).apply(any(NotificationChannel.class), any(), any());
    }

    @Test
    @DisplayName("Twilio: 'queued' and 'sent' are not news - the row was created in that state")
    void twilioIgnoresPreDeliveryStates() throws Exception {
        Map<String, String> form = twilioForm("sent");
        String signature = twilioSignature(BASE_URL + "/api/v1/webhooks/twilio/status", form, TOKEN);

        twilio(BASE_URL, TOKEN).status(form, signature);

        verify(deliveryStatusService).apply(eq(NotificationChannel.SMS), eq("SM123"), isNull());
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String sign(KeyPair pair, String timestamp, String body) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private SendGridEventWebhookController sendgrid(String publicKey) {
        return new SendGridEventWebhookController(
                new NotificationWebhookProperties(null, publicKey), deliveryStatusService, new ObjectMapper());
    }

    @Test
    @DisplayName("SendGrid: a batch signed with the configured key is applied, matching rows by the X-Message-Id prefix")
    void sendgridAcceptsAValidSignature() throws Exception {
        KeyPair pair = ecKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String body = "[{\"event\":\"delivered\",\"sg_message_id\":\"msg-abc-123.filterdrecv-p3iad2-1\"},"
                + "{\"event\":\"open\",\"sg_message_id\":\"msg-abc-123.filterdrecv-p3iad2-1\"},"
                + "{\"event\":\"bounce\",\"sg_message_id\":\"msg-xyz-999.filterdrecv-x\"},"
                + "{\"event\":\"processed\",\"sg_message_id\":\"msg-abc-123.filterdrecv-p3iad2-1\"}]";
        String timestamp = "1789300000";

        assertThat(sendgrid(publicKey).events(body, sign(pair, timestamp, body), timestamp).getStatusCode().value()).isEqualTo(200);

        verify(deliveryStatusService).apply(NotificationChannel.EMAIL, "msg-abc-123", NotificationStatus.DELIVERED);
        verify(deliveryStatusService).apply(NotificationChannel.EMAIL, "msg-abc-123", NotificationStatus.READ);
        verify(deliveryStatusService).apply(NotificationChannel.EMAIL, "msg-xyz-999", NotificationStatus.FAILED);
        verify(deliveryStatusService).apply(eq(NotificationChannel.EMAIL), eq("msg-abc-123"), isNull());
    }

    @Test
    @DisplayName("SendGrid: another key's signature, a tampered body, or no configured key is 403")
    void sendgridRefusesTheRest() throws Exception {
        KeyPair real = ecKeyPair();
        KeyPair other = ecKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(real.getPublic().getEncoded());
        String body = "[{\"event\":\"delivered\",\"sg_message_id\":\"msg-1.x\"}]";
        String timestamp = "1789300000";

        assertThat(sendgrid(publicKey).events(body, sign(other, timestamp, body), timestamp).getStatusCode().value()).isEqualTo(403);
        assertThat(sendgrid(publicKey).events(body + " ", sign(real, timestamp, body), timestamp).getStatusCode().value()).isEqualTo(403);
        assertThat(sendgrid("").events(body, sign(real, timestamp, body), timestamp).getStatusCode().value()).isEqualTo(403);
        verify(deliveryStatusService, never()).apply(any(NotificationChannel.class), any(), any());
    }

    @Test
    @DisplayName("SendGrid: the message id is everything before the first dot of sg_message_id")
    void sendgridMessageIdPrefix() {
        assertThat(SendGridEventWebhookController.messageIdOf("abc.filterdrecv-1")).isEqualTo("abc");
        assertThat(SendGridEventWebhookController.messageIdOf("abc")).isEqualTo("abc");
        assertThat(SendGridEventWebhookController.messageIdOf("")).isNull();
        assertThat(SendGridEventWebhookController.messageIdOf(null)).isNull();
    }
}
