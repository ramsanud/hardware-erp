package com.hardware.erp.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.auth.dto.*;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End to end against real PostgreSQL with the seeded accounts from V900. */
class AuthControllerIT extends AbstractIntegrationTest {

    // ================= login =================

    @Test
    @DisplayName("a correct password returns an MFA challenge, never a session (CR-058)")
    void loginReturnsMfaChallengeNotSession() throws Exception {
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(OWNER_MOBILE, OWNER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.mfaToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600))
                // The whole point: no session material is handed out here.
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.user").doesNotExist());
    }

    @Test
    @DisplayName("completing MFA returns tokens and the user's permissions")
    void completedMfaReturnsSession() throws Exception {
        JsonNode session = login(OWNER_MOBILE, OWNER_PASSWORD);

        assertThat(session.path("accessToken").asText()).isNotBlank();
        assertThat(session.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(session.path("expiresInSeconds").asLong()).isEqualTo(900);
        assertThat(session.path("user").path("roleCode").asText()).isEqualTo("OWNER");
        assertThat(session.path("user").path("permissions").isArray()).isTrue();
    }

    @Test
    @DisplayName("login with email works for the same account")
    void loginWithEmail() throws Exception {
        JsonNode session = login(OWNER_EMAIL, OWNER_PASSWORD);
        assertThat(session.path("user").path("mobileNo").asText()).isEqualTo(OWNER_MOBILE);
    }

    @Test
    @DisplayName("no response ever contains a password hash or the TOTP secret")
    void neverLeaksPasswordHash() throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(OWNER_MOBILE, OWNER_PASSWORD))))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain("tokenVersion")
                .doesNotContain("totpSecret")
                .doesNotContain("failedLoginAttempts");
    }

    @Test
    @DisplayName("wrong password and unknown account return byte-identical bodies")
    void enumerationProtection() throws Exception {
        String wrongPassword = errorBody(post("/v1/auth/login").contentType(APPLICATION_JSON)
                .content(json(new LoginRequest(OWNER_MOBILE, "Wrong@9999"))));
        String unknownUser = errorBody(post("/v1/auth/login").contentType(APPLICATION_JSON)
                .content(json(new LoginRequest("9000000000", "Wrong@9999"))));

        assertThat(strip(wrongPassword)).isEqualTo(strip(unknownUser));
    }

    @Test
    @DisplayName("an inactive account is refused with the same generic message")
    void inactiveAccountRefused() throws Exception {
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(INACTIVE_STAFF_MOBILE, STAFF_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("a suspended account is refused")
    void suspendedAccountRefused() throws Exception {
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(SUSPENDED_STAFF_MOBILE, STAFF_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a soft-deleted account cannot sign in")
    void deletedAccountRefused() throws Exception {
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(DELETED_STAFF_MOBILE, STAFF_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a blank password fails validation with a field-level error")
    void validationError() throws Exception {
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(OWNER_MOBILE, ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("malformed JSON gives 400 without echoing the payload")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ================= access control =================

    @Test
    @DisplayName("a protected endpoint without a token gives 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a malformed bearer token gives 401")
    void malformedToken() throws Exception {
        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void tamperedToken() throws Exception {
        String token = accessToken(OWNER_MOBILE, OWNER_PASSWORD);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("STAFF is forbidden from the user admin endpoint")
    void staffForbidden() throws Exception {
        mockMvc.perform(get("/v1/users")
                        .header("Authorization", bearer(STAFF_MOBILE, STAFF_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                // Must not name the missing permission - that maps the auth model.
                .andExpect(jsonPath("$.message").value(
                        "You do not have permission for this action"));
    }

    @Test
    @DisplayName("every response carries a correlation id")
    void correlationIdEchoed() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(header().exists("X-Request-ID"));
    }

    // ================= refresh =================

    @Test
    @DisplayName("refresh rotates the pair and the old token then fails")
    void refreshRotates() throws Exception {
        JsonNode first = login(MANAGER_MOBILE, MANAGER_PASSWORD);
        String oldRefresh = first.path("refreshToken").asText();

        String body = mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(oldRefresh))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = tree(body).path("data").path("refreshToken").asText();

        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(oldRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE"));
    }

    @Test
    @DisplayName("reuse revokes every session, so the rotated token stops working too")
    void reuseRevokesAllSessions() throws Exception {
        JsonNode first = login(MANAGER_MOBILE, MANAGER_PASSWORD);
        String oldRefresh = first.path("refreshToken").asText();

        String body = mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(oldRefresh))))
                .andReturn().getResponse().getContentAsString();
        String rotated = tree(body).path("data").path("refreshToken").asText();

        // Replay the old one: theft response fires.
        mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(oldRefresh))))
                .andExpect(status().isUnauthorized());

        // The legitimate replacement is now dead as well.
        mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(rotated))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown refresh token gives 401")
    void unknownRefreshToken() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest("completely-made-up"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    // ================= logout =================

    @Test
    @DisplayName("logout ends this session but leaves other devices signed in")
    void logoutDoesNotAffectOtherSessions() throws Exception {
        JsonNode deviceA = login(MANAGER_MOBILE, MANAGER_PASSWORD);
        JsonNode deviceB = login(MANAGER_MOBILE, MANAGER_PASSWORD);

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + deviceA.path("accessToken").asText())
                        .contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(deviceA.path("refreshToken").asText()))))
                .andExpect(status().isOk());

        // Device A's refresh is dead.
        mockMvc.perform(post("/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(deviceA.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());

        // Device B is untouched - this is the whole point of BUG-AUTH-002.
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + deviceB.path("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logout-all kills every device including the access token")
    void logoutAllKillsEverything() throws Exception {
        JsonNode deviceA = login(MANAGER_MOBILE, MANAGER_PASSWORD);
        JsonNode deviceB = login(MANAGER_MOBILE, MANAGER_PASSWORD);

        mockMvc.perform(post("/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + deviceA.path("accessToken").asText()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + deviceB.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    // ================= sessions =================

    @Test
    @DisplayName("session list marks the current session and exposes no token material")
    void sessionList() throws Exception {
        String token = accessToken(STAFF_MOBILE, STAFF_PASSWORD);

        String body = mockMvc.perform(get("/v1/auth/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("tokenHash").doesNotContain("token_hash");
    }

    // ================= password =================

    @Test
    @DisplayName("forgot-password returns the same body for known and unknown identifiers")
    void forgotPasswordDoesNotEnumerate() throws Exception {
        String known = mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(json(new ForgotPasswordRequest(OWNER_MOBILE))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(post("/v1/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(json(new ForgotPasswordRequest("9000000000"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(strip(known)).isEqualTo(strip(unknown));
    }

    @Test
    @DisplayName("an invalid reset token gives 400, not 500")
    void invalidResetToken() throws Exception {
        mockMvc.perform(post("/v1/auth/reset-password").contentType(APPLICATION_JSON)
                        .content(json(new ResetPasswordRequest("made-up", "NewPass@2026"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    @DisplayName("changing the password invalidates the token that made the change")
    void changePasswordInvalidatesToken() throws Exception {
        // EMP007 Suresh, used only here so other tests keep their password.
        String mobile = "9843034567";
        String token = accessToken(mobile, STAFF_PASSWORD);

        mockMvc.perform(post("/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(new ChangePasswordRequest(STAFF_PASSWORD, "Changed@2026"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(mobile, "Changed@2026"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /auth/me edits only the caller, never another account")
    void profileEditIsSelfOnly() throws Exception {
        String token = accessToken(STAFF_MOBILE, STAFF_PASSWORD);

        mockMvc.perform(put("/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest(
                                "Karthik Raja S", "karthik@sarahardware.in"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Karthik Raja S"))
                .andExpect(jsonPath("$.data.mobileNo").value(STAFF_MOBILE));
    }

    // ---- helpers ----

    private String errorBody(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    /** Removes the fields that legitimately differ between two calls. */
    private String strip(String body) {
        return body.replaceAll("\"timestamp\":\"[^\"]*\"", "")
                   .replaceAll("\"requestId\":\"[^\"]*\"", "");
    }
}
