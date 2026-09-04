package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.service.FeatureFlagService;
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

/** Real PostgreSQL - CR-057 phase 8 (Feature Flags). */
class PlatformAdminFeatureFlagControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;
    @Autowired private FeatureFlagService featureFlagService;

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
    @DisplayName("create, enable, and FeatureFlagService.isEnabled() reflects it - the real backend enforcement point")
    void createEnableAndCheckIsEnabled() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.DEVELOPER, "flags-create@platform.test");
        String flagKey = "test-flag-" + System.nanoTime();

        assertThat(featureFlagService.isEnabled(flagKey)).isFalse();

        String createBody = mockMvc.perform(post("/v1/platform-admin/feature-flags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"flagKey":"%s","name":"Test Flag","description":"desc","scope":"GLOBAL"}
                                """.formatted(flagKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andReturn().getResponse().getContentAsString();
        long flagId = tree(createBody).path("data").path("id").asLong();

        assertThat(featureFlagService.isEnabled(flagKey)).isFalse();

        mockMvc.perform(post("/v1/platform-admin/feature-flags/" + flagId + "/enable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(featureFlagService.isEnabled(flagKey)).isTrue();
    }

    @Test
    @DisplayName("a duplicate flag key is refused")
    void duplicateFlagKeyRefused() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.DEVELOPER, "flags-dup@platform.test");
        String flagKey = "dup-flag-" + System.nanoTime();
        String body = """
                {"flagKey":"%s","name":"n","description":null,"scope":"GLOBAL"}
                """.formatted(flagKey);

        mockMvc.perform(post("/v1/platform-admin/feature-flags")
                        .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/platform-admin/feature-flags")
                        .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a role without FEATURE_FLAG_MANAGE (SUPPORT_ADMIN) can view but not create")
    void viewOnlyRoleCannotCreate() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.SUPPORT_ADMIN, "flags-view-only@platform.test");

        mockMvc.perform(get("/v1/platform-admin/feature-flags")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // SUPPORT_ADMIN lacks FEATURE_FLAG_VIEW too in this session's role table

        mockMvc.perform(post("/v1/platform-admin/feature-flags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"flagKey":"x","name":"x","scope":"GLOBAL"}
                                """))
                .andExpect(status().isForbidden());
    }
}
