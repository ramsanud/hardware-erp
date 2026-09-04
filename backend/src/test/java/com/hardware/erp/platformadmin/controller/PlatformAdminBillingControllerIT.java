package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.security.totp.TotpService;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL - CR-057 phase 9 (Subscriptions & Billing), platform-admin side. */
class PlatformAdminBillingControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;

    private String platformAdminToken(PlatformAdminRole role, String email) throws Exception {
        PlatformAdmin admin = platformAdminRepository.save(PlatformAdmin.builder()
                .fullName("Test Admin").email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(role).status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(true).totpSecret(totpService.generateSecret())
                .tokenVersion(0).failedLoginAttempts(0).build());

        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();
        String code = totpService.currentCode(admin.getTotpSecret());
        String sessionBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andReturn().getResponse().getContentAsString();
        return tree(sessionBody).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("overview() reports razorpayConfigured:false honestly and 12 months of real aggregates, no gateway configured")
    void overviewHonestlyReportsNotConfigured() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.FINANCE_ADMIN, "billing-overview@platform.test");

        mockMvc.perform(get("/v1/platform-admin/billing/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.razorpayConfigured").value(false))
                .andExpect(jsonPath("$.data.monthly.length()").value(12));
    }

    @Test
    @DisplayName("tenantHistory() returns the seeded tenant's current tier and payment list from real tables")
    void tenantHistoryReturnsRealData() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.FINANCE_ADMIN, "billing-tenant-history@platform.test");

        mockMvc.perform(get("/v1/platform-admin/billing/tenants/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTier").exists())
                .andExpect(jsonPath("$.data.payments").isArray());
    }

    @Test
    @DisplayName("a role without BILLING_VIEW (DEVELOPER) is refused")
    void roleWithoutBillingViewRefused() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.DEVELOPER, "billing-no-access@platform.test");

        mockMvc.perform(get("/v1/platform-admin/billing/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
