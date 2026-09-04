package com.hardware.erp.billing.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.billing.service.RazorpayOrderClient;
import com.hardware.erp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Real Razorpay Orders API integration. Never called unless the resolved
 * EffectiveRazorpayConfig.active() - callers check that first and raise an
 * honest "billing not configured" error rather than reaching this class
 * with blank credentials (same fail-closed shape as TurnstileCaptchaService).
 */
@Slf4j
@Service
public class RazorpayOrderClientImpl implements RazorpayOrderClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String createOrder(String keyId, String keySecret, String apiBaseUrl,
                               long amountPaise, String currency, String receipt) {
        String credentials = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        String body = """
                {"amount":%d,"currency":"%s","receipt":"%s","payment_capture":1}
                """.formatted(amountPaise, escape(currency), escape(receipt)).strip();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/orders"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + credentials)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (response.statusCode() != 200 || json.path("id").isMissingNode()) {
                log.error("Razorpay order creation failed: HTTP {} - {}", response.statusCode(), response.body());
                throw new BusinessException(
                        "Could not start checkout with the payment gateway. Please try again shortly.",
                        HttpStatus.SERVICE_UNAVAILABLE, "RAZORPAY_UNAVAILABLE");
            }
            return json.path("id").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private BusinessException unavailable(Exception cause) {
        log.error("Could not reach Razorpay", cause);
        return new BusinessException(
                "The payment gateway is temporarily unavailable. Please try again shortly.",
                HttpStatus.SERVICE_UNAVAILABLE, "RAZORPAY_UNAVAILABLE");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
