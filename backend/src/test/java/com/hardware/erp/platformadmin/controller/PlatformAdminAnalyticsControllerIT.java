package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.service.TotpService;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL - CR-057 phase 10 (Tenant Analytics). The main reason
 * this exists as an IT rather than only a mocked unit test:
 * TenantAnalyticsServiceImpl.moduleUsage() runs real interpolated SQL
 * (`select count(distinct m.tenant_id) from <table> m join tenant t ...`)
 * against 8 real table names - exactly the kind of thing a mocked
 * JdbcTemplate cannot catch a typo in. This proves every one of those
 * table names is real and the query executes against the actual schema
 * seeded from V900.
 */
class PlatformAdminAnalyticsControllerIT extends AbstractIntegrationTest {

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
    @DisplayName("overview() runs real module-usage SQL against all 8 real tables and returns 12 months of growth/churn")
    void overviewReturnsRealAggregates() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.FINANCE_ADMIN, "analytics-overview@platform.test");

        mockMvc.perform(get("/v1/platform-admin/analytics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.growth.length()").value(12))
                .andExpect(jsonPath("$.data.churn.length()").value(12))
                .andExpect(jsonPath("$.data.moduleUsage.length()").value(8))
                .andExpect(jsonPath("$.data.activeTenantsNow").isNumber());
    }

    @Test
    @DisplayName("CSV/XLSX/PDF export all succeed with the right content type, from the same real data")
    void exportProducesAllThreeFormats() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.FINANCE_ADMIN, "analytics-export@platform.test");

        mockMvc.perform(get("/v1/platform-admin/analytics/export").param("format", "csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));

        mockMvc.perform(get("/v1/platform-admin/analytics/export").param("format", "xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        mockMvc.perform(get("/v1/platform-admin/analytics/export").param("format", "pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"));
    }

    @Test
    @DisplayName("SUPPORT_ADMIN holds ANALYTICS_VIEW (can see the overview) but not ANALYTICS_EXPORT (cannot export)")
    void viewOnlyRoleCannotExport() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.SUPPORT_ADMIN, "analytics-view-only@platform.test");

        mockMvc.perform(get("/v1/platform-admin/analytics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/platform-admin/analytics/export").param("format", "csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
