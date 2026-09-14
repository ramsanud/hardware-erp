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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-077 - the email channel over Resend.
 *
 * Mirrors SendGridEmailProviderTest deliberately: the two providers must be
 * interchangeable behind EmailTransport, so the same questions are asked of
 * both. Where Resend's API differs (a flat body, "Name <addr>" as one string,
 * the id in the body rather than a header) the difference is pinned here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResendEmailProviderTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4 };

    @Mock private HttpClient httpClient;
    @SuppressWarnings("unchecked")
    @Mock private HttpResponse<String> httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ResendProperties configured() {
        return new ResendProperties("https://api.resend.com", "re_key", "billing@sarahardware.in", "Sara Hardware");
    }

    private ResendEmailProvider providerWith(ResendProperties properties) {
        return new ResendEmailProvider(properties, objectMapper, httpClient);
    }

    /** Resend answers 200 with the id in the body. */
    private void stubAccepted() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);
    }

    @Test
    @DisplayName("with no API key the message is logged, not sent")
    void unconfiguredLogsInsteadOfSending() throws Exception {
        ResendEmailProvider provider = providerWith(new ResendProperties("https://api.resend.com", "", "", null));

        NotificationSendResult result = provider.send(
                1L, NotificationChannel.EMAIL, "ramesh@example.com", "Invoice INV-1", "body");

        assertThat(result.status()).isEqualTo(NotificationStatus.LOGGED_ONLY);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("an API key with no verified from address is not configured - Resend would answer 403")
    void aKeyWithoutAFromAddressIsNotConfigured() {
        assertThat(new ResendProperties("url", "re_key", "", null).isConfigured()).isFalse();
        assertThat(new ResendProperties("url", "", "a@b.in", null).isConfigured()).isFalse();
        assertThat(new ResendProperties("url", "re_key", "a@b.in", null).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("200 returns SENT carrying the id from Resend's response body")
    void acceptedSendReturnsTheMessageIdFromTheBody() throws Exception {
        stubAccepted();

        NotificationSendResult result = providerWith(configured())
                .send(1L, NotificationChannel.EMAIL, "ramesh@example.com", "Invoice INV-1", "body");

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.providerMessageId()).isEqualTo("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794");
    }

    @Test
    @DisplayName("the payload matches Resend's flat schema, with the sender as one 'Name <addr>' string")
    void payloadMatchesResendsSchema() throws Exception {
        JsonNode payload = objectMapper.readTree(
                providerWith(configured()).buildPayload("ramesh@example.com", "Invoice INV-1", "Your invoice", null));

        assertThat(payload.path("from").asText()).isEqualTo("Sara Hardware <billing@sarahardware.in>");
        assertThat(payload.path("to").get(0).asText()).isEqualTo("ramesh@example.com");
        assertThat(payload.path("subject").asText()).isEqualTo("Invoice INV-1");
        assertThat(payload.path("text").asText()).isEqualTo("Your invoice");
        assertThat(payload.has("attachments")).isFalse();
    }

    @Test
    @DisplayName("without a display name the sender is the bare address, not '<addr>' or 'null <addr>'")
    void aMissingFromNameSendsTheBareAddress() throws Exception {
        JsonNode payload = objectMapper.readTree(providerWith(
                new ResendProperties("https://api.resend.com", "re_key", "billing@sarahardware.in", " "))
                .buildPayload("ramesh@example.com", "s", "b", null));

        assertThat(payload.path("from").asText()).isEqualTo("billing@sarahardware.in");
    }

    @Test
    @DisplayName("a missing subject becomes a placeholder - Resend rejects a missing one with a 422")
    void aBlankSubjectIsReplacedRatherThanSentEmpty() throws Exception {
        JsonNode payload = objectMapper.readTree(
                providerWith(configured()).buildPayload("ramesh@example.com", null, "body", null));

        assertThat(payload.path("subject").asText()).isEqualTo("(no subject)");
    }

    @Test
    @DisplayName("an attachment is base64-encoded into the payload under Resend's snake_case key")
    void anAttachmentIsBase64EncodedIntoThePayload() throws Exception {
        JsonNode payload = objectMapper.readTree(providerWith(configured()).buildPayload(
                "admin@erp.in", "[Support] broken", "body",
                new NotificationAttachment("bug.png", "image/png", PNG)));

        JsonNode attachment = payload.path("attachments").get(0);
        assertThat(attachment.path("filename").asText()).isEqualTo("bug.png");
        assertThat(attachment.path("content_type").asText()).isEqualTo("image/png");
        assertThat(Base64.getDecoder().decode(attachment.path("content").asText())).isEqualTo(PNG);
    }

    @Test
    @DisplayName("the request is a bearer-authenticated POST to /emails")
    void requestShapeMatchesTheApi() throws Exception {
        stubAccepted();

        providerWith(configured()).send(1L, NotificationChannel.EMAIL, "ramesh@example.com", "s", "b");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(captor.getValue().method()).isEqualTo("POST");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer re_key");
        assertThat(captor.getValue().headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    @DisplayName("a Resend rejection throws, carrying Resend's own message")
    void aRejectedSendThrowsWithTheProvidersOwnExplanation() throws Exception {
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn(
                "{\"statusCode\":403,\"name\":\"validation_error\","
                        + "\"message\":\"The sarahardware.in domain is not verified.\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> providerWith(configured())
                .send(1L, NotificationChannel.EMAIL, "ramesh@example.com", "s", "b"))
                .isInstanceOf(ResendEmailProvider.ResendSendException.class)
                .hasMessageContaining("domain is not verified");
    }

    @Test
    @DisplayName("as an EmailTransport it reports its own configuration state and a Resend-specific hint")
    void itAnswersTheEmailTransportQuestions() {
        ResendEmailProvider configured = providerWith(configured());
        assertThat(configured.isConfigured()).isTrue();
        assertThat(configured.senderAddress()).isEqualTo("billing@sarahardware.in");

        ResendEmailProvider unconfigured = providerWith(new ResendProperties("https://api.resend.com", "", "", null));
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.senderAddress()).isNull();
        assertThat(unconfigured.unconfiguredHint()).contains("RESEND_API_KEY");
    }

    @Test
    @DisplayName("it claims only the EMAIL channel")
    void itClaimsOnlyTheEmailChannel() {
        assertThat(providerWith(configured()).supportedChannels())
                .containsExactly(NotificationChannel.EMAIL);
    }
}
