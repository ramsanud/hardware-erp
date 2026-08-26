package com.hardware.erp.security.captcha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Verifies a Cloudflare Turnstile token server-side.
 *
 * The widget's client-side token proves nothing on its own - it has to be
 * exchanged with Cloudflare, from the server, using the secret key. Skipping
 * that step is the classic way a CAPTCHA ends up decorative.
 *
 * Chosen over reCAPTCHA because it is free at any volume (this app is
 * documented to run on free hosting tiers), sends nothing to an ad network,
 * and is usually invisible to real users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnstileCaptchaService implements CaptchaService {

    private static final String FAILED_CODE = "CAPTCHA_FAILED";

    private final CaptchaProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean active() {
        return properties.active();
    }

    @Override
    public String siteKey() {
        return properties.active() ? properties.siteKey() : null;
    }

    @Override
    public void verify(String token, String remoteIp) {
        if (!properties.active()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(
                    "Please complete the security check.", HttpStatus.BAD_REQUEST, FAILED_CODE);
        }

        try {
            StringBuilder body = new StringBuilder()
                    .append("secret=").append(encode(properties.secretKey()))
                    .append("&response=").append(encode(token));
            // Cloudflare uses the IP only as a signal; sending a proxy's address
            // would weaken it, so it is omitted rather than guessed.
            if (remoteIp != null && !remoteIp.isBlank()) {
                body.append("&remoteip=").append(encode(remoteIp));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.verifyUrl()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (!json.path("success").asBoolean(false)) {
                // Cloudflare's codes are diagnostic, not user-facing: they name
                // the site's own misconfiguration as often as a failed human.
                log.warn("Turnstile rejected a token: {}", json.path("error-codes"));
                throw new BusinessException(
                        "Security check failed. Please try again.", HttpStatus.BAD_REQUEST, FAILED_CODE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    /**
     * Cloudflare being unreachable must not silently wave everyone through -
     * that turns a network blip into an open door - but it must also not be
     * reported as the user's mistake, or they will retry the challenge forever
     * against a service that is down.
     */
    private BusinessException unavailable(Exception cause) {
        log.error("Could not reach the CAPTCHA service", cause);
        return new BusinessException(
                "The security check is temporarily unavailable. Please try again shortly.",
                HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA_UNAVAILABLE");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
