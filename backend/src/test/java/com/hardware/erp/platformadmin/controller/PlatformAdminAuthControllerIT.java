package com.hardware.erp.platformadmin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.platformadmin.dto.*;
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

/**
 * Real PostgreSQL, real filter chain, real security config - the same
 * Testcontainers style as AuthControllerIT, but for the completely separate
 * /v1/platform-admin/** chain (PlatformAdminSecurityConfig). No seed data
 * exists for platform admins, so each test creates exactly the accounts it
 * needs directly through the repository.
 */
class PlatformAdminAuthControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;

    private PlatformAdmin persistAdmin(PlatformAdminRole role, boolean mfaEnabled, String email) {
        PlatformAdmin.PlatformAdminBuilder builder = PlatformAdmin.builder()
                .fullName("Test Admin")
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(role)
                .status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(mfaEnabled)
                .tokenVersion(0)
                .failedLoginAttempts(0);
        if (mfaEnabled) {
            builder.totpSecret(totpService.generateSecret());
        }
        return platformAdminRepository.save(builder.build());
    }

    // ================= full enrollment + login flow =================

    @Test
    @DisplayName("a fresh account must enroll MFA before it gets a session, then logs straight in")
    void enrollThenSessionIssued() throws Exception {
        String email = "fresh-admin@platform.test";
        persistAdmin(PlatformAdminRole.SUPER_ADMIN, false, email);

        // Step 1: password check only - never a session.
        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentRequired").value(true))
                .andExpect(jsonPath("$.data.mfaToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();

        // Step 2: start enrollment - get a real TOTP secret back.
        String enrollBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/enroll")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaTokenRequest(mfaToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretBase32").isNotEmpty())
                .andExpect(jsonPath("$.data.qrCodePngBase64").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = tree(enrollBody).path("data").path("secretBase32").asText();

        // Step 3: confirm with a real, freshly computed code - and get a session immediately.
        String code = currentCodeFor(secret);
        String confirmBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/enroll/confirm")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.backupCodes.length()").value(10))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = tree(confirmBody).path("data");
        String accessToken = data.path("session").path("accessToken").asText();

        mockMvc.perform(get("/v1/platform-admin/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.mfaEnabled").value(true))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    @Test
    @DisplayName("an already-enrolled account verifies with a real TOTP code")
    void loginWithExistingMfa() throws Exception {
        String email = "enrolled-admin@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, true, email);

        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();

        String code = currentCodeFor(admin.getTotpSecret());
        mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.admin.role").value("PLATFORM_ADMIN"));
    }

    @Test
    @DisplayName("a wrong TOTP code is rejected without issuing a session")
    void wrongMfaCodeRejected() throws Exception {
        String email = "wrong-code-admin@platform.test";
        persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, true, email);

        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();

        mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, "000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
    }

    @Test
    @DisplayName("a backup code logs an admin in once, then is refused on reuse")
    void backupCodeUsedOnceThenRejected() throws Exception {
        String email = "backup-code-admin@platform.test";
        persistAdmin(PlatformAdminRole.SUPER_ADMIN, false, email);

        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String enrollMfaToken = tree(loginBody).path("data").path("mfaToken").asText();

        String enrollBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/enroll")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaTokenRequest(enrollMfaToken))))
                .andReturn().getResponse().getContentAsString();
        String secret = tree(enrollBody).path("data").path("secretBase32").asText();

        String confirmBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/enroll/confirm")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(enrollMfaToken, currentCodeFor(secret)))))
                .andReturn().getResponse().getContentAsString();
        String backupCode = tree(confirmBody).path("data").path("backupCodes").get(0).asText();

        // A fresh login, MFA-verified with the backup code instead of a TOTP code.
        String secondLoginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String secondMfaToken = tree(secondLoginBody).path("data").path("mfaToken").asText();

        mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(secondMfaToken, backupCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Same code, a third login: already consumed.
        String thirdLoginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String thirdMfaToken = tree(thirdLoginBody).path("data").path("mfaToken").asText();

        mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(thirdMfaToken, backupCode))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
    }

    @Test
    @DisplayName("wrong password and unknown email return byte-identical bodies")
    void enumerationProtection() throws Exception {
        persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, true, "known-admin@platform.test");

        String wrongPassword = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(
                                "known-admin@platform.test", "Wrong@9999"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(
                                "nobody@platform.test", "Wrong@9999"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(strip(wrongPassword)).isEqualTo(strip(unknownEmail));
    }

    // ================= cross-boundary isolation =================

    @Test
    @DisplayName("a tenant session token is refused on a platform-admin endpoint")
    void tenantTokenRefusedOnPlatformAdminEndpoint() throws Exception {
        String tenantToken = accessToken(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(get("/v1/platform-admin/auth/me")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a platform-admin session token is refused on a tenant endpoint")
    void platformAdminTokenRefusedOnTenantEndpoint() throws Exception {
        String email = "cross-boundary-admin@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, true, email);
        String accessToken = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    // ================= RBAC =================

    @Test
    @DisplayName("a non-SUPER_ADMIN role gets 403 creating another platform admin")
    void nonSuperAdminCannotCreate() throws Exception {
        String email = "support-admin@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SUPPORT_ADMIN, true, email);
        String accessToken = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/admins")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreatePlatformAdminRequest(
                                "New Admin", "new-admin@platform.test",
                                "AnotherStrong@2026", PlatformAdminRole.SUPPORT_ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN can create and then list platform admins")
    void superAdminCanCreateAndList() throws Exception {
        String email = "root-admin@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.SUPER_ADMIN, true, email);
        String accessToken = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/admins")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreatePlatformAdminRequest(
                                "New Admin", "created-by-super@platform.test",
                                "AnotherStrong@2026", PlatformAdminRole.SUPPORT_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaEnabled").value(false))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/v1/platform-admin/admins")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='created-by-super@platform.test')]").exists());
    }

    // ================= refresh rotation =================

    @Test
    @DisplayName("refresh rotates the token and the old one becomes unusable")
    void refreshRotates() throws Exception {
        String email = "refresh-admin@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, true, email);

        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();
        String code = currentCodeFor(admin.getTotpSecret());

        String sessionBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = tree(sessionBody).path("data").path("refreshToken").asText();

        String rotatedBody = mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminRefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = tree(rotatedBody).path("data").path("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // Replaying the rotated-away token is theft: every session for this admin is revoked.
        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminRefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE"));

        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminRefreshRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private String currentCodeFor(String base32Secret) {
        return totpService.currentCode(base32Secret);
    }

    private String fullyAuthenticate(String email, String totpSecret) throws Exception {
        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();
        String code = currentCodeFor(totpSecret);
        String sessionBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andReturn().getResponse().getContentAsString();
        return tree(sessionBody).path("data").path("accessToken").asText();
    }

    private String strip(String errorBody) throws Exception {
        return ((com.fasterxml.jackson.databind.node.ObjectNode) tree(errorBody))
                .remove(java.util.List.of("requestId", "timestamp")).toString();
    }
}
