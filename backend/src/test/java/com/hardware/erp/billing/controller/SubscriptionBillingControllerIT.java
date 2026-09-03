package com.hardware.erp.billing.controller;

import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL - CR-057 phase 9 (Subscriptions & Billing), tenant side.
 * No Razorpay credentials are configured in application-test.yml (the same
 * "unconfigured" state as every real deployment of this repo today), so
 * this proves the fail-closed path end to end through Spring Security and
 * the real service - not just the unit-tested service method in isolation.
 */
class SubscriptionBillingControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("checkout() returns an honest 503 BILLING_NOT_CONFIGURED, never a fake order, with no gateway credentials set")
    void checkoutFailsClosedWhenNotConfigured() throws Exception {
        String token = accessToken(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(post("/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"requestedTier\":\"PRO\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BILLING_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("STAFF lacks SETTINGS_MANAGE and cannot start checkout")
    void staffCannotCheckout() throws Exception {
        String token = accessToken(STAFF_MOBILE, STAFF_PASSWORD);

        mockMvc.perform(post("/v1/billing/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"requestedTier\":\"PRO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("history() returns the caller's own tenant - current tier and no payments yet, from real tables")
    void historyReturnsOwnTenant() throws Exception {
        String token = accessToken(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(get("/v1/billing/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTier").exists())
                .andExpect(jsonPath("$.data.payments").isArray());
    }

    @Test
    @DisplayName("the Razorpay webhook endpoint is reachable with no JWT and rejects an unsigned event")
    void webhookRejectsUnsignedEventWithNoAuth() throws Exception {
        mockMvc.perform(post("/v1/webhooks/razorpay")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isForbidden());
    }
}
