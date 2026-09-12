package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationStatus;
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
 * CR-074 - the SMS channel became a real Twilio send.
 *
 * These assert what actually goes on the wire (the form body, the auth
 * header, the E.164 conversion) rather than mocking the send and trusting
 * it: a stub that was replaced by a real HTTP call is only worth testing at
 * the point where a wrong field name or a missing "+" would silently mean
 * no message reaches anybody.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwilioSmsProviderTest {

    private static final String ACCOUNT_SID = "AC0000000000000000000000000000cafe";

    @Mock private HttpClient httpClient;
    @SuppressWarnings("unchecked")
    @Mock private HttpResponse<String> httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SmsNotificationProvider providerWith(TwilioProperties properties) {
        return new SmsNotificationProvider(properties, objectMapper, httpClient);
    }

    private TwilioProperties configured() {
        return new TwilioProperties("https://api.twilio.com/2010-04-01", ACCOUNT_SID, "auth-token-value",
                "+15550001111", null);
    }

    @Test
    @DisplayName("with no credentials the message is logged, not sent - a deployment without Twilio keeps working")
    void unconfiguredLogsInsteadOfSending() throws Exception {
        SmsNotificationProvider provider = providerWith(
                new TwilioProperties("https://api.twilio.com/2010-04-01", "", "", "", ""));

        NotificationSendResult result = provider.send(
                1L, NotificationChannel.SMS, "9876543210", null, "Your invoice INV-1 is ready.");

        assertThat(result.status()).isEqualTo(NotificationStatus.LOGGED_ONLY);
        assertThat(result.providerMessageId()).isNull();
        // The point of LOGGED_ONLY is that nothing was attempted at all.
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("an account SID and token with no sender at all is still unconfigured")
    void credentialsWithoutASenderAreNotConfigured() {
        assertThat(new TwilioProperties("url", ACCOUNT_SID, "token", "", "").isConfigured()).isFalse();
        assertThat(new TwilioProperties("url", ACCOUNT_SID, "token", "+15550001111", null).isConfigured()).isTrue();
        assertThat(new TwilioProperties("url", ACCOUNT_SID, "token", null, "MG123").isConfigured()).isTrue();
        assertThat(new TwilioProperties("url", "", "token", "+15550001111", null).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("a 201 from Twilio returns SENT carrying the message SID")
    void acceptedSendReturnsTheTwilioSid() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"sid\":\"SM123abc\",\"status\":\"queued\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        NotificationSendResult result = providerWith(configured())
                .send(1L, NotificationChannel.SMS, "9876543210", null, "Balance due");

        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        // Kept in notification_log.provider_message_id - the only handle on the
        // message if the customer later says it never arrived.
        assertThat(result.providerMessageId()).isEqualTo("SM123abc");
    }

    @Test
    @DisplayName("the request carries Basic auth and the form fields Twilio actually expects")
    void requestShapeMatchesTwiliosApi() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"sid\":\"SM1\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        providerWith(configured()).send(1L, NotificationChannel.SMS, "9876543210", null, "Balance due");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertThat(request.uri().toString())
                .isEqualTo("https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Content-Type"))
                .contains("application/x-www-form-urlencoded");

        // Basic, not Bearer - Twilio differs from Meta's Cloud API here, and
        // getting it wrong fails with a 401 that says nothing about why.
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString((ACCOUNT_SID + ":auth-token-value").getBytes());
        assertThat(request.headers().firstValue("Authorization")).contains(expected);
    }

    @Test
    @DisplayName("a stored 10-digit Indian mobile becomes +91-prefixed E.164")
    void indianMobileNumbersAreConvertedToE164() {
        SmsNotificationProvider provider = providerWith(configured());

        // Every Customer/Supplier/User in this app stores bare 10 digits.
        assertThat(provider.toE164("9876543210")).isEqualTo("+919876543210");
        assertThat(provider.toE164(" 98765 43210 ")).isEqualTo("+919876543210");
        // Already international - assuming +91 would send it to the wrong country.
        assertThat(provider.toE164("+14155550123")).isEqualTo("+14155550123");
    }

    @Test
    @DisplayName("a Messaging Service SID wins over a plain From - it is what carries the DLT sender id")
    void messagingServiceTakesPrecedenceOverFromNumber() {
        SmsNotificationProvider provider = providerWith(new TwilioProperties(
                "https://api.twilio.com/2010-04-01", ACCOUNT_SID, "token", "+15550001111", "MG999"));

        String form = provider.formBody("9876543210", "Balance due");

        assertThat(form).contains("MessagingServiceSid=MG999");
        assertThat(form).doesNotContain("From=");
        assertThat(form).contains("To=%2B919876543210");
        assertThat(form).contains("Body=Balance+due");
    }

    @Test
    @DisplayName("with no Messaging Service the purchased number is sent as From")
    void fromNumberIsUsedWhenNoMessagingServiceIsSet() {
        String form = providerWith(configured()).formBody("9876543210", "Hi");

        assertThat(form).contains("From=%2B15550001111");
        assertThat(form).doesNotContain("MessagingServiceSid");
    }

    /**
     * The provider contract: a configured provider that cannot deliver throws,
     * and NotificationServiceImpl is the single place that turns that into a
     * FAILED row. Returning FAILED from here would write that row twice.
     */
    @Test
    @DisplayName("a Twilio error throws, carrying Twilio's own code and message")
    void aRejectedSendThrowsWithTheProvidersOwnExplanation() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn(
                "{\"code\":21608,\"message\":\"The number is unverified. Trial accounts may only send to verified numbers.\"}");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> providerWith(configured())
                .send(1L, NotificationChannel.SMS, "9876543210", null, "Balance due"))
                .isInstanceOf(SmsNotificationProvider.SmsSendException.class)
                .hasMessageContaining("21608")
                .hasMessageContaining("Trial accounts may only send to verified numbers");
    }

    @Test
    @DisplayName("a non-JSON error body is still reported rather than swallowed")
    void aNonJsonErrorBodyIsReportedVerbatim() throws Exception {
        when(httpResponse.statusCode()).thenReturn(502);
        when(httpResponse.body()).thenReturn("<html>Bad Gateway</html>");
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> providerWith(configured())
                .send(1L, NotificationChannel.SMS, "9876543210", null, "Balance due"))
                .isInstanceOf(SmsNotificationProvider.SmsSendException.class)
                .hasMessageContaining("Bad Gateway");
    }

    @Test
    @DisplayName("it still claims only the SMS channel - two providers on one channel collide silently")
    void itClaimsOnlyTheSmsChannel() {
        assertThat(providerWith(configured()).supportedChannels())
                .containsExactly(NotificationChannel.SMS);
    }
}
