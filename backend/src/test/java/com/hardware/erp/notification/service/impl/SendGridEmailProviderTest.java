package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.notification.service.NotificationSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-074 - the email channel over Twilio SendGrid.
 *
 * The payload is asserted by parsing it back, not by string matching: SendGrid
 * rejects a structurally wrong body with a 400 whose message names a field
 * path, so the nesting (personalizations[].to[].email, content[].type) is the
 * part actually worth pinning down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendGridEmailProviderTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4 };

    @Mock private HttpClient httpClient;
    @SuppressWarnings("unchecked")
    @Mock private HttpResponse<String> httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SendGridProperties configured() {
        return new SendGridProperties("https://api.sendgrid.com/v3", "SG.key", "billing@sarahardware.in", "Sara Hardware");
    }

    private SendGridEmailProvider providerWith(SendGridProperties properties) {
        return new SendGridEmailProvider(properties, objectMapper, httpClient);
    }

    /** SendGrid answers 202 with an empty body and the id in a header. */
    private void stubAccepted() throws Exception {
        when(httpResponse.statusCode()).thenReturn(202);
        when(httpResponse.body()).thenReturn("");
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(
                Map.of("X-Message-Id", List.of("msg-abc-123")), (k, v) -> true));
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);
    }

    @Test
    @DisplayName("with no API key the message is logged, not sent")
    void unconfiguredLogsInsteadOfSending() throws Exception {
        SendGridEmailProvider provider = providerWith(
                new SendGridProperties("https://api.sendgrid.com/v3", "", "", null));

        NotificationSendResult result = provider.send(
                1L, NotificationChannel.EMAIL, "ramesh@example.com", "Invoice INV-1", "body");

        assertThat(result.status()).isEqualTo(NotificationStatus.LOGGED_ONLY);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("an API key with no verified from address is not configured - SendGrid would answer 403")
    void aKeyWithoutAFromAddressIsNotConfigured() {
        assertThat(new SendGridProperties("url", "SG.key", "", null).isConfigured()).isFalse();
        assertThat(new SendGridProperties("url", "", "a@b.in", null).isConfigured()).isFalse();
        assertThat(new SendGridProperties("url", "SG.key", "a@b.in", null).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("202 Accepted returns SENT carrying SendGrid's X-Message-Id")
    void acceptedSendReturnsTheMessageIdFromTheHeader() throws Exception {
        stubAccepted();

        NotificationSendResult result = providerWith(configured())
                .send(1L, NotificationChannel.EMAIL, "ramesh@example.com", "Invoice INV-1", "body");

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        // The whole reason for using the v3 API over SendGrid's SMTP relay:
        // SMTP hands back nothing to record.
        assertThat(result.providerMessageId()).isEqualTo("msg-abc-123");
    }

    @Test
    @DisplayName("the payload matches the nesting SendGrid's v3 Mail Send schema requires")
    void payloadMatchesTheV3Schema() throws Exception {
        JsonNode payload = objectMapper.readTree(
                providerWith(configured()).buildPayload("ramesh@example.com", "Invoice INV-1", "Your invoice", null));

        assertThat(payload.path("personalizations").get(0).path("to").get(0).path("email").asText())
                .isEqualTo("ramesh@example.com");
        assertThat(payload.path("from").path("email").asText()).isEqualTo("billing@sarahardware.in");
        assertThat(payload.path("from").path("name").asText()).isEqualTo("Sara Hardware");
        assertThat(payload.path("subject").asText()).isEqualTo("Invoice INV-1");
        assertThat(payload.path("content").get(0).path("type").asText()).isEqualTo("text/plain");
        assertThat(payload.path("content").get(0).path("value").asText()).isEqualTo("Your invoice");
        assertThat(payload.has("attachments")).isFalse();
    }

    @Test
    @DisplayName("a missing subject becomes a placeholder - SendGrid rejects an empty one with a 400")
    void aBlankSubjectIsReplacedRatherThanSentEmpty() throws Exception {
        JsonNode payload = objectMapper.readTree(
                providerWith(configured()).buildPayload("ramesh@example.com", null, "body", null));

        assertThat(payload.path("subject").asText()).isEqualTo("(no subject)");
    }

    @Test
    @DisplayName("an attachment is base64-encoded into the payload, not dropped")
    void anAttachmentIsBase64EncodedIntoThePayload() throws Exception {
        JsonNode payload = objectMapper.readTree(providerWith(configured()).buildPayload(
                "admin@erp.in", "[Support] broken", "body",
                new NotificationAttachment("bug.png", "image/png", PNG)));

        JsonNode attachment = payload.path("attachments").get(0);
        assertThat(attachment.path("filename").asText()).isEqualTo("bug.png");
        assertThat(attachment.path("type").asText()).isEqualTo("image/png");
        assertThat(attachment.path("disposition").asText()).isEqualTo("attachment");
        assertThat(Base64.getDecoder().decode(attachment.path("content").asText())).isEqualTo(PNG);
    }

    @Test
    @DisplayName("the request is a bearer-authenticated POST to /mail/send")
    void requestShapeMatchesTheApi() throws Exception {
        stubAccepted();

        providerWith(configured()).send(1L, NotificationChannel.EMAIL, "ramesh@example.com", "s", "b");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.sendgrid.com/v3/mail/send");
        assertThat(captor.getValue().method()).isEqualTo("POST");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer SG.key");
        assertThat(captor.getValue().headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    @DisplayName("a SendGrid rejection throws, carrying SendGrid's own message")
    void aRejectedSendThrowsWithTheProvidersOwnExplanation() throws Exception {
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn(
                "{\"errors\":[{\"message\":\"The from address does not match a verified Sender Identity.\"}]}");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> providerWith(configured())
                .send(1L, NotificationChannel.EMAIL, "ramesh@example.com", "s", "b"))
                .isInstanceOf(SendGridEmailProvider.SendGridSendException.class)
                .hasMessageContaining("verified Sender Identity");
    }

    /**
     * EmailTransport is what password reset, invoice mail and the Settings
     * diagnostic now go through, so the provider must answer these the same
     * way the SMTP one does - not just implement the interface nominally.
     */
    @Test
    @DisplayName("as an EmailTransport it reports its own configuration state and a SendGrid-specific hint")
    void itAnswersTheEmailTransportQuestions() {
        SendGridEmailProvider configured = providerWith(configured());
        assertThat(configured.isConfigured()).isTrue();
        assertThat(configured.senderAddress()).isEqualTo("billing@sarahardware.in");

        SendGridEmailProvider unconfigured = providerWith(
                new SendGridProperties("https://api.sendgrid.com/v3", "", "", null));
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.senderAddress()).isNull();
        // Telling a SendGrid deployment to "set MAIL_USER" sends the owner to
        // entirely the wrong place.
        assertThat(unconfigured.unconfiguredHint()).contains("SENDGRID_API_KEY");
    }

    @Test
    @DisplayName("it claims only the EMAIL channel")
    void itClaimsOnlyTheEmailChannel() {
        assertThat(providerWith(configured()).supportedChannels())
                .containsExactly(NotificationChannel.EMAIL);
    }
}
