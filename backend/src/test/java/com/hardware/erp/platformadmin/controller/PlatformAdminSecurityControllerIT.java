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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL - CR-057 phase 6 (Security Center). */
class PlatformAdminSecurityControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;

    private PlatformAdmin persistAdmin(PlatformAdminRole role, String email) {
        return platformAdminRepository.save(PlatformAdmin.builder()
                .fullName("Test Admin").email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(role).status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(true).totpSecret(totpService.generateSecret())
                .tokenVersion(0).failedLoginAttempts(0).build());
    }

    private String fullyAuthenticate(PlatformAdmin admin) throws Exception {
        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(admin.getEmail(), RAW_PASSWORD))))
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
    @DisplayName("logging in twice shows two active sessions, revoking one leaves exactly one")
    void sessionsListAndRevoke() throws Exception {
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SECURITY_ADMIN, "sessions@platform.test");
        String firstToken = fullyAuthenticate(admin);
        String secondToken = fullyAuthenticate(admin);

        String listBody = mockMvc.perform(get("/v1/platform-admin/security/sessions")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long firstSessionId = tree(listBody).path("data").get(1).path("id").asLong();

        mockMvc.perform(post("/v1/platform-admin/security/sessions/" + firstSessionId + "/revoke")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/platform-admin/security/sessions")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("an admin cannot revoke another admin's session by guessing its id")
    void cannotRevokeAnotherAdminsSession() throws Exception {
        PlatformAdmin adminA = persistAdmin(PlatformAdminRole.SECURITY_ADMIN, "session-a@platform.test");
        PlatformAdmin adminB = persistAdmin(PlatformAdminRole.SECURITY_ADMIN, "session-b@platform.test");
        String tokenA = fullyAuthenticate(adminA);
        String tokenB = fullyAuthenticate(adminB);

        String listBodyA = mockMvc.perform(get("/v1/platform-admin/security/sessions")
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getContentAsString();
        long sessionIdA = tree(listBodyA).path("data").get(0).path("id").asLong();

        mockMvc.perform(post("/v1/platform-admin/security/sessions/" + sessionIdA + "/revoke")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("security dashboard returns real MFA coverage and session counts")
    void dashboardReturnsRealCounts() throws Exception {
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SECURITY_ADMIN, "dashboard@platform.test");
        String token = fullyAuthenticate(admin);

        String body = mockMvc.perform(get("/v1/platform-admin/security/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(tree(body).path("data").path("totalAdmins").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(tree(body).path("data").path("adminsWithMfaEnabled").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(tree(body).path("data").path("activeSessions").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a role without SECURITY_VIEW (SUPPORT_ADMIN) is refused the dashboard")
    void roleWithoutSecurityViewIsRefused() throws Exception {
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SUPPORT_ADMIN, "no-security-view@platform.test");
        String token = fullyAuthenticate(admin);

        mockMvc.perform(get("/v1/platform-admin/security/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
