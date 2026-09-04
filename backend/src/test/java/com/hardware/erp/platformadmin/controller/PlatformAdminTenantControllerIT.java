package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import com.hardware.erp.security.totp.TotpService;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL, real filter chain - CR-054 phase 2 (Tenant Management).
 * Reuses the seed tenant (tenant_id 1, "sarahardware", real users/data from
 * V900) so list/detail return genuine non-trivial counts, not an empty
 * fixture built just for this test.
 */
class PlatformAdminTenantControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PlatformAuditLogRepository platformAuditLogRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;

    private PlatformAdmin persistAdmin(PlatformAdminRole role, String email) {
        return platformAdminRepository.save(PlatformAdmin.builder()
                .fullName("Test Admin")
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(role)
                .status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(true)
                .totpSecret(totpService.generateSecret())
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .build());
    }

    private String fullyAuthenticate(String email, String totpSecret) throws Exception {
        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();
        String code = totpService.currentCode(totpSecret);
        String sessionBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andReturn().getResponse().getContentAsString();
        return tree(sessionBody).path("data").path("accessToken").asText();
    }

    // ================= list / detail =================

    @Test
    @DisplayName("TENANT_VIEW lists tenants with real usage/owner data, not placeholders")
    void listReturnsRealData() throws Exception {
        String email = "view-only@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.READ_ONLY_AUDITOR, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/tenants")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].slug").value("default"))
                .andExpect(jsonPath("$.data.content[0].ownerName").value("Saravanan Murugan"))
                .andExpect(jsonPath("$.data.content[0].userCount").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("tenant detail returns real aggregate usage counts")
    void detailReturnsUsageCounts() throws Exception {
        String email = "view-detail@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.READ_ONLY_AUDITOR, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/tenants/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Default Shop"))
                .andExpect(jsonPath("$.data.usage.users").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("an unknown tenant id is a real 404, not a 200 with nulls")
    void unknownTenantIs404() throws Exception {
        String email = "view-404@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.READ_ONLY_AUDITOR, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/tenants/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ================= RBAC: view vs manage =================

    @Test
    @DisplayName("READ_ONLY_AUDITOR can view but gets 403 suspending a tenant")
    void readOnlyCannotSuspend() throws Exception {
        String email = "auditor-suspend@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.READ_ONLY_AUDITOR, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/tenants/1/suspend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("suspending with a blank reason is rejected by validation")
    void suspendRequiresReason() throws Exception {
        String email = "support-blank-reason@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SUPPORT_ADMIN, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/tenants/1/suspend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ================= suspend / reactivate lifecycle =================

    @Test
    @DisplayName("SUPPORT_ADMIN suspends a tenant, it is audited under the acting admin, and double-suspend is refused")
    void suspendAuditedAndIdempotencyGuarded() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug("suspend-target-" + System.nanoTime())
                .name("Suspend Target Co")
                .status(TenantStatus.ACTIVE)
                .build());

        String email = "support-suspender@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SUPPORT_ADMIN, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/tenants/" + tenant.getId() + "/suspend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Payment issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        boolean audited = platformAuditLogRepository.findAll().stream()
                .anyMatch(entry -> entry.getAction() == PlatformAuditAction.TENANT_SUSPENDED
                        && tenant.getId().equals(entry.getTargetId())
                        && admin.getId().equals(entry.getAdminId())
                        && "Payment issue".equals(entry.getDetail()));
        assertThat(audited).isTrue();

        // Already suspended - a second attempt is a business error, not a silent no-op.
        mockMvc.perform(post("/v1/platform-admin/tenants/" + tenant.getId() + "/suspend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Payment issue again\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Reactivate brings it back, also audited.
        mockMvc.perform(post("/v1/platform-admin/tenants/" + tenant.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        boolean reactivationAudited = platformAuditLogRepository.findAll().stream()
                .anyMatch(entry -> entry.getAction() == PlatformAuditAction.TENANT_REACTIVATED
                        && tenant.getId().equals(entry.getTargetId()));
        assertThat(reactivationAudited).isTrue();
    }

    // ================= cross-boundary isolation =================

    @Test
    @DisplayName("a tenant session token is refused on the platform-admin tenant list")
    void tenantTokenRefused() throws Exception {
        String tenantToken = accessToken(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(get("/v1/platform-admin/tenants")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isUnauthorized());
    }
}
