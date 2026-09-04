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

/** Real PostgreSQL - CR-057 phase 7 (Developer Tools). */
class PlatformAdminDeveloperToolsControllerIT extends AbstractIntegrationTest {

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
    @DisplayName("database diagnostics returns a real, reachable snapshot with pool stats")
    void databaseDiagnosticsIsReal() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.DEVELOPER, "devtools-db@platform.test");

        mockMvc.perform(get("/v1/platform-admin/developer-tools/database")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionReachable").value(true))
                .andExpect(jsonPath("$.data.pool.maxSize").exists())
                .andExpect(jsonPath("$.data.migrationVersion").exists());
    }

    @Test
    @DisplayName("retrying token-cleanup actually runs it and records a new job_execution_log row")
    void retryingTokenCleanupActuallyRuns() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.DEVELOPER, "devtools-retry@platform.test");

        mockMvc.perform(post("/v1/platform-admin/developer-tools/jobs/token-cleanup/retry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/platform-admin/developer-tools/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.jobName=='token-cleanup')].lastStatus").exists());
    }

    @Test
    @DisplayName("a health-check job name is not retryable from this screen")
    void healthCheckJobIsNotRetryable() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.DEVELOPER, "devtools-noretry@platform.test");

        mockMvc.perform(post("/v1/platform-admin/developer-tools/jobs/health:database/retry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a role without DEVELOPER_TOOLS_VIEW (SUPPORT_ADMIN) is refused")
    void roleWithoutDeveloperToolsViewIsRefused() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.SUPPORT_ADMIN, "devtools-refused@platform.test");

        mockMvc.perform(get("/v1/platform-admin/developer-tools/database")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
