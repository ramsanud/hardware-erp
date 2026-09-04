package com.hardware.erp.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.auth.dto.LoginRequest;
import com.hardware.erp.auth.dto.MfaTokenRequest;
import com.hardware.erp.auth.dto.MfaVerifyRequest;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL - CR-058, mandatory TOTP MFA for every tenant user.
 *
 * Uses its own freshly created account rather than a seeded one, so the
 * enrollment-from-scratch path is exercised for real regardless of what any
 * other test in the suite has already enrolled.
 */
class UserMfaFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Welcome@2026";

    private String newUserMobile() throws Exception {
        String mobile = "97000" + String.format("%05d", (int) (System.nanoTime() % 100000));
        String ownerAuth = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        mockMvc.perform(post("/v1/users").header("Authorization", ownerAuth)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"MFA Test User","mobileNo":"%s",
                                 "email":"mfa-%s@sarahardware.in","employeeCode":"EMP%s",
                                 "roleId":3,"password":"%s","mustChangePassword":false}
                                """.formatted(mobile, mobile, mobile.substring(5), PASSWORD)))
                .andExpect(status().isCreated());
        return mobile;
    }

    private JsonNode challenge(String identifier, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(identifier, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    @Test
    @DisplayName("a brand-new account must enroll before it is ever given a session")
    void enrollmentIsForcedBeforeFirstSession() throws Exception {
        String mobile = newUserMobile();

        JsonNode first = challenge(mobile, PASSWORD);
        assertThat(first.path("enrollmentRequired").asBoolean()).isTrue();
        assertThat(first.path("mfaToken").asText()).isNotBlank();
        assertThat(first.path("accessToken").isMissingNode()).isTrue();

        // The enrollment challenge token is not a session token.
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + first.path("mfaToken").asText()))
                .andExpect(status().isUnauthorized());

        String enrollBody = mockMvc.perform(post("/v1/auth/mfa/enroll")
                        .contentType(APPLICATION_JSON)
                        .content(json(new MfaTokenRequest(first.path("mfaToken").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpAuthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andExpect(jsonPath("$.data.qrCodePngBase64").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = tree(enrollBody).path("data").path("secretBase32").asText();

        // A wrong code does not complete enrollment.
        mockMvc.perform(post("/v1/auth/mfa/enroll/confirm").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(first.path("mfaToken").asText(), "000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));

        String confirmBody = mockMvc.perform(post("/v1/auth/mfa/enroll/confirm")
                        .contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(first.path("mfaToken").asText(),
                                totpService.currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.backupCodes").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode confirmed = tree(confirmBody).path("data");
        assertThat(confirmed.path("backupCodes")).hasSize(10);

        // That session really works.
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + confirmed.path("session").path("accessToken").asText()))
                .andExpect(status().isOk());

        // A second login no longer asks to enroll - it asks for a code.
        JsonNode second = challenge(mobile, PASSWORD);
        assertThat(second.path("enrollmentRequired").asBoolean()).isFalse();

        // A wrong code is refused...
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(second.path("mfaToken").asText(), "000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));

        // ...and a real one signs in.
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(second.path("mfaToken").asText(),
                                totpService.currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("a one-time backup code signs in once and is then dead")
    void backupCodeIsSingleUse() throws Exception {
        String mobile = newUserMobile();

        // Enroll by hand so the recovery codes - shown exactly once - are captured.
        String enrollToken = challenge(mobile, PASSWORD).path("mfaToken").asText();
        String enrollBody = mockMvc.perform(post("/v1/auth/mfa/enroll")
                        .contentType(APPLICATION_JSON)
                        .content(json(new MfaTokenRequest(enrollToken))))
                .andReturn().getResponse().getContentAsString();
        String secret = tree(enrollBody).path("data").path("secretBase32").asText();

        String confirmBody = mockMvc.perform(post("/v1/auth/mfa/enroll/confirm")
                        .contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(enrollToken, totpService.currentCode(secret)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode backupCodes = tree(confirmBody).path("data").path("backupCodes");
        assertThat(backupCodes).hasSize(10);
        String recoveryCode = backupCodes.get(0).asText();

        User user = userRepository.findByIdentifier(mobile).orElseThrow();
        assertThat(user.isMfaEnabled()).isTrue();

        // The recovery code signs in, standing in for the authenticator app.
        String firstToken = challenge(mobile, PASSWORD).path("mfaToken").asText();
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(firstToken, recoveryCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // The same code is now spent and must not work a second time.
        String secondToken = challenge(mobile, PASSWORD).path("mfaToken").asText();
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(secondToken, recoveryCode))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));
    }

    @Test
    @DisplayName("an enrollment token cannot be used on /mfa/verify, nor a login token on /mfa/enroll")
    void purposeIsEnforced() throws Exception {
        String mobile = newUserMobile();
        String enrollToken = challenge(mobile, PASSWORD).path("mfaToken").asText();

        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(enrollToken, "123456"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_TOKEN_INVALID"));

        login(mobile, PASSWORD); // completes enrollment
        String loginToken = challenge(mobile, PASSWORD).path("mfaToken").asText();

        mockMvc.perform(post("/v1/auth/mfa/enroll").contentType(APPLICATION_JSON)
                        .content(json(new MfaTokenRequest(loginToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("a made-up MFA token is refused")
    void forgedMfaTokenRefused() throws Exception {
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest("not.a.real.token", "123456"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_TOKEN_INVALID"));
    }
}
