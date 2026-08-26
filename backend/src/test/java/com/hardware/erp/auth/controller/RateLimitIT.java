package com.hardware.erp.auth.controller;

import com.hardware.erp.auth.dto.ForgotPasswordRequest;
import com.hardware.erp.auth.dto.LoginRequest;
import com.hardware.erp.security.ratelimit.RateLimitService;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rate limiting is disabled in application-test.yml so it does not throttle
 * unrelated tests. This class switches it on for itself only.
 *
 * The exact-count assertions are also the regression test for BUG-AUTH-008:
 * when both filters were @Component each request consumed two tokens, so the
 * limit tripped at half the configured value.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login-per-ip-per-minute=5",
        "app.rate-limit.login-per-identifier-per-minute=100",
        "app.rate-limit.forgot-password-per-ip-per-hour=3",
        "app.rate-limit.forgot-password-per-identifier-per-hour=100"
})
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired private RateLimitService rateLimitService;

    @BeforeEach
    void clearBuckets() {
        rateLimitService.reset();
    }

    @Test
    @DisplayName("login allows exactly 5 attempts per IP, then returns 429")
    void loginIsRateLimitedPerIp() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                            .content(json(new LoginRequest("9000000000", "Wrong@1234"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("9000000000", "Wrong@1234"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("the limit applies to valid credentials too, so it is not a lockout bypass")
    void limitAppliesToValidLogins() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                            .content(json(new LoginRequest(OWNER_MOBILE, OWNER_PASSWORD))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(OWNER_MOBILE, OWNER_PASSWORD))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("forgot-password allows exactly 3 requests per IP per hour")
    void forgotPasswordIsRateLimited() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/v1/auth/forgot-password").contentType(APPLICATION_JSON)
                            .content(json(new ForgotPasswordRequest(OWNER_MOBILE))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/v1/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content(json(new ForgotPasswordRequest(OWNER_MOBILE))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("the 429 body follows the standard error envelope and leaks nothing")
    void rateLimitBodyShape() throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                    .content(json(new LoginRequest("9000000001", "Wrong@1234"))));
        }

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("9000000001", "Wrong@1234"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.timestamp").exists())
                // ErrorResponse.path is the full request URI, so under a real
                // container with context-path /api it reads /api/v1/auth/login.
                // MockMvc applies no context path, so it reads /v1/auth/login
                // here. Asserting the suffix is true in both, rather than
                // encoding one environment's answer as the expected value -
                // this assertion had never actually executed before BUG-SEC-003
                // was fixed, because the request was refused with 401 several
                // lines earlier.
                .andExpect(jsonPath("$.path").value(endsWith("/v1/auth/login")))
                .andExpect(jsonPath("$.message").value(
                        "Too many requests. Please wait before trying again."));
    }

    @Test
    @DisplayName("unrelated endpoints are not throttled by the auth buckets")
    void otherEndpointsUnaffected() throws Exception {
        String token = accessToken(MANAGER_MOBILE, MANAGER_PASSWORD);

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request
                            .MockMvcRequestBuilders.get("/v1/auth/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }
}
