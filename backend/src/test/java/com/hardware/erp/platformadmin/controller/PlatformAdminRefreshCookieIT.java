package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-065 / BUG-SEC-006 - the Platform Admin refresh token must not be
 * readable by JavaScript.
 *
 * The suite-wide test profile runs refresh-token-transport=JSON because that
 * is simpler for every other test to assert on, so this class switches the
 * property rather than changing it globally - the same approach RateLimitIT
 * takes. That keeps PlatformAdminAuthControllerIT's existing body-transport
 * assertions meaningful (the JSON mode is still a supported configuration for
 * non-browser clients) while pinning the browser default here.
 */
@TestPropertySource(properties = "app.security.refresh-token-transport=COOKIE")
class PlatformAdminRefreshCookieIT extends AbstractIntegrationTest {

    private static final String COOKIE = "erp_pa_refresh_token";
    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // =================================================================
    // the cookie itself
    // =================================================================

    @Test
    @DisplayName("signing in sets an HttpOnly, SameSite=Strict cookie scoped to the platform-admin auth path")
    void signInSetsAHardenedCookie() throws Exception {
        MvcResult session = signIn("cookie-shape@platform.test");

        Cookie cookie = session.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("the refresh cookie must be set on sign-in").isNotNull();
        assertThat(cookie.getValue()).isNotBlank();

        // HttpOnly is the whole point: this is what an XSS bug cannot read.
        assertThat(cookie.isHttpOnly()).as("HttpOnly").isTrue();

        // Path-scoped, so a staff credential is never attached to a tenant
        // request made by any shop.
        assertThat(cookie.getPath()).isEqualTo("/api/v1/platform-admin/auth");

        // SameSite is not exposed on the Cookie object, so read the header.
        String setCookie = session.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(COOKIE + "="))
                .findFirst().orElseThrow();
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("the refresh token is absent from the response body in cookie mode")
    void refreshTokenNeverReachesTheBody() throws Exception {
        MvcResult session = signIn("cookie-body@platform.test");
        String body = session.getResponse().getContentAsString();

        // The access token still comes back - only the 7-day credential moves
        // out of reach.
        assertThat(tree(body).path("data").path("accessToken").asText()).isNotBlank();

        // asText(""), not isNull(): Jackson omits null fields entirely, so the
        // node is MISSING rather than null and isNull() would be false for the
        // very outcome we want. This covers both shapes.
        assertThat(tree(body).path("data").path("refreshToken").asText(""))
                .as("refreshToken must not be readable by JavaScript")
                .isEmpty();

        // The strongest form of the same claim: the cookie's actual value must
        // appear nowhere in the payload the browser can read.
        String cookieValue = session.getResponse().getCookie(COOKIE).getValue();
        assertThat(cookieValue).isNotBlank();
        assertThat(body)
                .as("the raw refresh credential must not appear anywhere in the response body")
                .doesNotContain(cookieValue);
    }

    @Test
    @DisplayName("the platform-admin cookie name differs from the tenant one, so the two sessions cannot collide")
    void cookieNameIsDistinctFromTheTenantCookie() throws Exception {
        MvcResult session = signIn("cookie-distinct@platform.test");
        assertThat(session.getResponse().getCookie(COOKIE)).isNotNull();
        // erp_refresh_token is the tenant cookie (SecurityProperties default).
        assertThat(session.getResponse().getCookie("erp_refresh_token")).isNull();
    }

    // =================================================================
    // the refresh flow, and page reload
    // =================================================================

    @Test
    @DisplayName("refresh works from the cookie alone - no body, nothing held in JavaScript")
    void refreshWorksFromTheCookieAlone() throws Exception {
        MvcResult session = signIn("cookie-refresh@platform.test");
        Cookie refreshCookie = session.getResponse().getCookie(COOKIE);

        // Exactly what the browser sends after a page reload: the cookie, and
        // an empty body, because the frontend has nothing else left to send.
        MvcResult rotated = mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(tree(rotated.getResponse().getContentAsString())
                .path("data").path("accessToken").asText()).isNotBlank();

        // Rotation still happens - the cookie comes back with a new value.
        Cookie rotatedCookie = rotated.getResponse().getCookie(COOKIE);
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());
        assertThat(rotatedCookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("replaying a rotated-away cookie is still treated as theft")
    void replayingARotatedCookieIsRefused() throws Exception {
        MvcResult session = signIn("cookie-reuse@platform.test");
        Cookie original = session.getResponse().getCookie(COOKIE);

        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{}").cookie(original))
                .andExpect(status().isOk());

        // The rotation detection is untouched by the transport change - this
        // is the assertion that proves CR-065 did not weaken it.
        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{}").cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE"));
    }

    @Test
    @DisplayName("refresh with no cookie at all is refused, not treated as anonymous")
    void refreshWithoutACookieIsRefused() throws Exception {
        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a forged cookie value is refused")
    void forgedCookieIsRefused() throws Exception {
        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{}")
                        .cookie(new Cookie(COOKIE, "not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // logout
    // =================================================================

    @Test
    @DisplayName("logout clears the cookie and the cleared token can never mint another session")
    void logoutClearsTheCookieAndRevokesTheToken() throws Exception {
        MvcResult session = signIn("cookie-logout@platform.test");
        Cookie refreshCookie = session.getResponse().getCookie(COOKIE);
        String accessToken = tree(session.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        MvcResult loggedOut = mockMvc.perform(post("/v1/platform-admin/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON).content("{}")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        // Max-Age=0 is how a cookie is deleted; without it the browser keeps
        // sending a token the server has forgotten.
        Cookie cleared = loggedOut.getResponse().getCookie(COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getValue()).isEmpty();

        // And the revocation is real, not just a cookie wipe.
        mockMvc.perform(post("/v1/platform-admin/auth/refresh")
                        .contentType(APPLICATION_JSON).content("{}").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // the boundary is not weakened
    // =================================================================

    @Test
    @DisplayName("an unauthenticated caller still cannot reach a platform-admin API")
    void unauthenticatedCallerIsRefused() throws Exception {
        mockMvc.perform(get("/v1/platform-admin/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a tenant access token cannot be escalated into the platform-admin console")
    void tenantTokenCannotReachThePlatformAdminConsole() throws Exception {
        String tenantToken = accessToken(OWNER_MOBILE, OWNER_PASSWORD);

        mockMvc.perform(get("/v1/platform-admin/auth/me")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the refresh cookie alone does not authenticate an ordinary API call")
    void theCookieIsNotABearerToken() throws Exception {
        MvcResult session = signIn("cookie-not-bearer@platform.test");
        Cookie refreshCookie = session.getResponse().getCookie(COOKIE);

        // The cookie mints access tokens; it is not itself an access token.
        mockMvc.perform(get("/v1/platform-admin/auth/me").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================

    /** Creates an MFA-enabled admin and drives the full two-step sign-in. */
    private MvcResult signIn(String email) throws Exception {
        PlatformAdmin admin = platformAdminRepository.save(PlatformAdmin.builder()
                .fullName("Cookie Test Admin")
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(PlatformAdminRole.PLATFORM_ADMIN)
                .status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(true)
                .totpSecret(totpService.generateSecret())
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .build());

        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();

        return mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(
                                mfaToken, totpService.currentCode(admin.getTotpSecret())))))
                .andExpect(status().isOk())
                .andReturn();
    }

}
