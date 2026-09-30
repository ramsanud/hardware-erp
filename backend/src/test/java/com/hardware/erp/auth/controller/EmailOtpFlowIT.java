package com.hardware.erp.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hardware.erp.auth.dto.LoginRequest;
import com.hardware.erp.auth.dto.MfaTokenRequest;
import com.hardware.erp.auth.dto.MfaVerifyRequest;
import com.hardware.erp.auth.entity.EmailOtpPurpose;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.service.MailService;
import com.hardware.erp.auth.service.OtpMailService;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-078 - every flow that runs on a code sent by email, end to end against
 * PostgreSQL, with the two mail senders replaced by recorders so the test
 * can read the code a person would have received.
 *
 * Both CR-078 switches are OFF for the rest of the suite (application-test.yml
 * says why); this class turns them on for itself. The @MockitoBean forks the
 * context once - accepted for the one class that needs the codes.
 */
@TestPropertySource(properties = {
        "app.security.mfa-email-fallback=true",
        "app.security.registration-email-verification=true",
})
class EmailOtpFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Welcome@2026";

    @MockitoBean private OtpMailService otpMailService;
    @MockitoBean private MailService mailService;
    @Autowired private com.hardware.erp.auth.repository.EmailOtpRepository otpRepository;

    /** email -> purpose -> last code "sent" to it. */
    private final Map<String, Map<EmailOtpPurpose, String>> codes = new ConcurrentHashMap<>();
    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    @BeforeEach
    void recordCodes() {
        doAnswer((InvocationOnMock call) -> {
            codes.computeIfAbsent(call.getArgument(0), k -> new ConcurrentHashMap<>())
                    .put(call.getArgument(2), call.getArgument(3));
            return null;
        }).when(otpMailService).sendCode(anyString(), any(), any(), anyString(), anyInt());
        doAnswer((InvocationOnMock call) -> {
            resetCodes.put(call.getArgument(0), call.getArgument(3));
            return null;
        }).when(mailService).sendPasswordResetLink(anyString(), any(), anyString(), any());
    }

    private String code(String email, EmailOtpPurpose purpose) {
        String code = codes.getOrDefault(email.toLowerCase(), Map.of()).get(purpose);
        assertThat(code).as("a %s code was sent to %s", purpose, email).isNotNull();
        return code;
    }

    /** The resend cooldown is real; tests that need a second code for the same address age the first one out. */
    private void expireCooldown(String email) {
        otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email.toLowerCase(), EmailOtpPurpose.LOGIN)
                .ifPresent(otp -> { otp.setCreatedAt(otp.getCreatedAt().minusSeconds(120)); otpRepository.save(otp); });
    }

    private JsonNode challenge(String identifier, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(identifier, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    /** Signs in whichever way the account is set up: email code, TOTP, or first-time TOTP enrollment. */
    private String bearerAnyWay(String identifier, String password) throws Exception {
        JsonNode c = challenge(identifier, password);
        if (c.path("session").isObject()) {
            return "Bearer " + c.path("session").path("accessToken").asText();
        }
        String mfaToken = c.path("mfaToken").asText();
        String code;
        if ("EMAIL".equals(c.path("mfaMethod").asText())) {
            User user = userRepository.findByIdentifier(identifier).orElseThrow();
            code = code(user.getEmail(), EmailOtpPurpose.LOGIN);
        } else if (c.path("enrollmentRequired").asBoolean()) {
            String enroll = mockMvc.perform(post("/v1/auth/mfa/enroll").contentType(APPLICATION_JSON)
                            .content(json(new MfaTokenRequest(mfaToken)))).andReturn().getResponse().getContentAsString();
            code = totpService.currentCode(tree(enroll).path("data").path("secretBase32").asText());
            String confirm = mockMvc.perform(post("/v1/auth/mfa/enroll/confirm").contentType(APPLICATION_JSON)
                            .content(json(new MfaVerifyRequest(mfaToken, code)))).andReturn().getResponse().getContentAsString();
            return "Bearer " + tree(confirm).path("data").path("session").path("accessToken").asText();
        } else {
            code = totpService.currentCode(userRepository.findByIdentifier(identifier).orElseThrow().getTotpSecret());
        }
        String verify = mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(mfaToken, code))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + tree(verify).path("data").path("accessToken").asText();
    }

    private String newUser(String ownerAuth, String mobile, String email) throws Exception {
        mockMvc.perform(post("/v1/users").header("Authorization", ownerAuth).contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"OTP Test User","mobileNo":"%s","email":"%s","employeeCode":"EMP%s",
                                 "roleId":3,"password":"%s","mustChangePassword":false}
                                """.formatted(mobile, email, mobile.substring(4), PASSWORD)))
                .andExpect(status().isCreated());
        return mobile;
    }

    private static String uniqueMobile() {
        return "96" + String.format("%08d", (System.nanoTime() / 1000) % 100_000_000L);
    }

    // -----------------------------------------------------------------
    // 1. Registration must prove the address first
    // -----------------------------------------------------------------

    @Test
    @DisplayName("registering a shop needs the code sent to the owner's email; the owner then has a verified email")
    void registrationRequiresTheEmailCode() throws Exception {
        String mobile = uniqueMobile();
        String email = "owner-" + mobile + "@newshop.in";
        String register = """
                {"shopName":"OTP Shop %s","ownerFullName":"OTP Owner","mobileNo":"%s","email":"%s",
                 "password":"Passw0rd","termsAccepted":true,"termsVersion":"1.0","privacyVersion":"1.0",
                 "marketingConsent":false%s}
                """;

        // No code at all, and a wrong code: refused before anything is created.
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(register.formatted(mobile, mobile, email, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        assertThat(userRepository.findByIdentifier(mobile)).isEmpty();

        mockMvc.perform(post("/v1/tenants/register/send-code").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailHint").value("o***" + mobile.charAt(9) + "@newshop.in"))
                .andExpect(jsonPath("$.data.resendAfterSeconds").value(60));

        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(register.formatted(mobile, mobile, email, ",\"emailCode\":\"000000\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));

        String code = code(email, EmailOtpPurpose.EMAIL_VERIFY);
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(register.formatted(mobile, mobile, email, ",\"emailCode\":\"" + code + "\"")))
                .andExpect(status().isCreated());

        User owner = userRepository.findByIdentifier(mobile).orElseThrow();
        assertThat(owner.getEmailVerifiedAt()).isNotNull();

        // The code is single-use: a second registration with it is refused.
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON)
                        .content(register.formatted(mobile + "x", uniqueMobile(), "again-" + email, ",\"emailCode\":\"" + code + "\"")))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------
    // 2. A user with no authenticator app signs in with an email code
    // -----------------------------------------------------------------

    @Test
    @DisplayName("without an authenticator the second factor is a code by email; a wrong code is refused, the right one signs in")
    void emailIsTheSecondFactorForAnUnenrolledUser() throws Exception {
        String ownerAuth = bearerAnyWay(OWNER_MOBILE, OWNER_PASSWORD);
        String mobile = uniqueMobile();
        String email = "staff-" + mobile + "@sarahardware.in";
        newUser(ownerAuth, mobile, email);

        JsonNode c = challenge(mobile, PASSWORD);
        assertThat(c.path("enrollmentRequired").asBoolean()).isFalse();
        assertThat(c.path("mfaMethod").asText()).isEqualTo("EMAIL");
        assertThat(c.path("emailHint").asText()).startsWith("s***").endsWith("@sarahardware.in").doesNotContain(mobile);
        String mfaToken = c.path("mfaToken").asText();

        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(mfaToken, "000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"));

        // A resend inside the cooldown is refused and says so - the password
        // was already checked, so there is nothing to hide here.
        mockMvc.perform(post("/v1/auth/mfa/email/resend").contentType(APPLICATION_JSON)
                        .content(json(new MfaTokenRequest(mfaToken))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_COOLDOWN"));

        String code = code(email, EmailOtpPurpose.LOGIN);
        String session = mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(mfaToken, code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.user.mfaEnabled").value(false))
                .andReturn().getResponse().getContentAsString();

        // The same code again is dead.
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(mfaToken, code))))
                .andExpect(status().isUnauthorized());

        // A TOTP code presented to an EMAIL challenge is never checked as TOTP:
        // the purpose was fixed when the password was checked.
        assertThat(tree(session).path("data").path("user").path("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("an authenticator added from the profile becomes the second factor at the next sign-in")
    void totpSetUpFromTheProfileReplacesTheEmailCode() throws Exception {
        String ownerAuth = bearerAnyWay(OWNER_MOBILE, OWNER_PASSWORD);
        String mobile = uniqueMobile();
        String email = "totp-" + mobile + "@sarahardware.in";
        newUser(ownerAuth, mobile, email);
        String auth = bearerAnyWay(mobile, PASSWORD);

        String setup = mockMvc.perform(post("/v1/auth/mfa/setup").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretBase32").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = tree(setup).path("data").path("secretBase32").asText();

        mockMvc.perform(post("/v1/auth/mfa/setup/confirm").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/auth/mfa/setup/confirm").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + totpService.currentCode(secret) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10));

        JsonNode next = challenge(mobile, PASSWORD);
        assertThat(next.path("mfaMethod").asText()).isEqualTo("TOTP");
        assertThat(next.path("emailHint").isMissingNode() || next.path("emailHint").isNull()).isTrue();
        mockMvc.perform(post("/v1/auth/mfa/verify").contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(next.path("mfaToken").asText(), totpService.currentCode(secret)))))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------
    // 3. Forgot password by code
    // -----------------------------------------------------------------

    @Test
    @DisplayName("the reset email carries a code that sets a new password; unknown identifiers and wrong codes are the same 400")
    void passwordResetByCode() throws Exception {
        String ownerAuth = bearerAnyWay(OWNER_MOBILE, OWNER_PASSWORD);
        String mobile = uniqueMobile();
        String email = "reset-" + mobile + "@sarahardware.in";
        newUser(ownerAuth, mobile, email);

        mockMvc.perform(post("/v1/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content("{\"identifier\":\"" + mobile + "\"}"))
                .andExpect(status().isOk());
        String code = resetCodes.get(email.toLowerCase());
        assertThat(code).as("a reset code travelled with the link").matches("\\d{6}");

        String reset = "{\"identifier\":\"%s\",\"code\":\"%s\",\"newPassword\":\"Changed@2026\"}";
        mockMvc.perform(post("/v1/auth/reset-password/code").contentType(APPLICATION_JSON)
                        .content(reset.formatted("9999999999", code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        mockMvc.perform(post("/v1/auth/reset-password/code").contentType(APPLICATION_JSON)
                        .content(reset.formatted(mobile, "000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        mockMvc.perform(post("/v1/auth/reset-password/code").contentType(APPLICATION_JSON)
                        .content(reset.formatted(mobile, code)))
                .andExpect(status().isOk());

        // The old password is gone, the new one works.
        mockMvc.perform(post("/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(mobile, PASSWORD))))
                .andExpect(status().isUnauthorized());
        assertThat(challenge(mobile, "Changed@2026").path("mfaToken").asText()).isNotBlank();
    }

    // -----------------------------------------------------------------
    // 4. Changing a verified login email needs a step-up
    // -----------------------------------------------------------------

    @Test
    @DisplayName("changing a verified login email needs a fresh code to the current address; the new address starts unverified")
    void emailChangeNeedsStepUp() throws Exception {
        String ownerAuth = bearerAnyWay(OWNER_MOBILE, OWNER_PASSWORD);
        String mobile = uniqueMobile();
        String email = "stepup-" + mobile + "@sarahardware.in";
        newUser(ownerAuth, mobile, email);
        String auth = bearerAnyWay(mobile, PASSWORD); // signing in by email code verified the address

        String profile = "{\"fullName\":\"OTP Test User\",\"email\":\"%s\"%s}";
        mockMvc.perform(put("/v1/auth/me").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content(profile.formatted("new-" + email, "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        mockMvc.perform(post("/v1/auth/step-up/send").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailHint").value("s***" + mobile.charAt(9) + "@sarahardware.in"));

        mockMvc.perform(post("/v1/auth/step-up/verify").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        String stepUp = mockMvc.perform(post("/v1/auth/step-up/verify").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code(email, EmailOtpPurpose.STEP_UP) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = tree(stepUp).path("data").path("stepUpToken").asText();

        // The step-up token is not a bearer token.
        mockMvc.perform(post("/v1/auth/step-up/send").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/v1/auth/me").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content(profile.formatted("new-" + email, ",\"stepUpToken\":\"" + token + "\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new-" + email))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        // Renaming without touching the email needs no step-up.
        mockMvc.perform(put("/v1/auth/me").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content(profile.formatted("new-" + email, "").replace("OTP Test User", "Renamed User")))
                .andExpect(status().isOk());
    }
}
