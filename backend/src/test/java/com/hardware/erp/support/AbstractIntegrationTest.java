package com.hardware.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.auth.dto.LoginRequest;
import com.hardware.erp.auth.dto.MfaTokenRequest;
import com.hardware.erp.auth.dto.MfaVerifyRequest;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.security.totp.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real PostgreSQL, not H2 (CR-014, BUG-AUTH-007).
 *
 * H2 in any compatibility mode fails to reproduce the things this schema
 * actually relies on: functional unique indexes on lower(email), partial
 * indexes with a WHERE clause, enforced CHECK constraints, SELECT FOR UPDATE
 * semantics, and identity column behaviour.
 *
 * The container is static, so one instance serves the whole suite. Starting one
 * per class would add roughly ten seconds each.
 *
 * Flyway runs V1 (schema) and V900 (seed) against it, so the tests exercise
 * exactly the rows documented in TEST_DATA.md.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hardware_erp_test")
                    .withUsername("erp")
                    .withPassword("erp")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    // ---- seeded accounts, from V900__seed_dev_data.sql ----
    protected static final String OWNER_MOBILE = "9876543210";
    protected static final String OWNER_EMAIL = "owner@sarahardware.in";
    protected static final String OWNER_PASSWORD = "Owner@2026";

    protected static final String SECOND_OWNER_MOBILE = "9876501234";
    protected static final String MANAGER_MOBILE = "9840112233";
    protected static final String MANAGER_PASSWORD = "Manager@2026";

    protected static final String STAFF_MOBILE = "9843012345";
    protected static final String STAFF_PASSWORD = "Staff@2026";

    protected static final String INACTIVE_STAFF_MOBILE = "9843056789";
    protected static final String SUSPENDED_STAFF_MOBILE = "9843067890";
    protected static final String DELETED_STAFF_MOBILE = "9843089012";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected TotpService totpService;
    @Autowired protected UserRepository userRepository;

    @BeforeEach
    void resetSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode tree(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    /**
     * Full login, transparently completing the CR-058 mandatory-MFA
     * challenge, and returning the parsed final-session data node so every
     * existing caller (accessToken/bearer, or a test reading refreshToken)
     * keeps working unchanged against the new two-step contract.
     *
     * Every seeded user starts with mfaEnabled=false, so the first call for
     * a given identifier in a test run enrolls it (the container is reused
     * across the suite via @ServiceConnection withReuse(true), so a later
     * call for the same identifier finds mfaEnabled already true and takes
     * the plain verify path instead).
     */
    protected JsonNode login(String identifier, String password) throws Exception {
        String challengeBody = mockMvc.perform(post("/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(identifier, password))))
                .andReturn().getResponse().getContentAsString();
        JsonNode challenge = tree(challengeBody).path("data");
        String mfaToken = challenge.path("mfaToken").asText(null);
        if (mfaToken == null || mfaToken.isBlank()) {
            // A non-200 login (wrong password, locked, etc.) - let the caller
            // see the raw error body exactly as before this helper existed.
            return tree(challengeBody).path("data");
        }

        if (challenge.path("enrollmentRequired").asBoolean(false)) {
            String enrollBody = mockMvc.perform(post("/v1/auth/mfa/enroll")
                            .contentType(APPLICATION_JSON)
                            .content(json(new MfaTokenRequest(mfaToken))))
                    .andReturn().getResponse().getContentAsString();
            String secret = tree(enrollBody).path("data").path("secretBase32").asText();
            String code = totpService.currentCode(secret);

            String confirmBody = mockMvc.perform(post("/v1/auth/mfa/enroll/confirm")
                            .contentType(APPLICATION_JSON)
                            .content(json(new MfaVerifyRequest(mfaToken, code))))
                    .andReturn().getResponse().getContentAsString();
            return tree(confirmBody).path("data").path("session");
        }

        User user = userRepository.findByIdentifier(identifier).orElseThrow();
        String code = totpService.currentCode(user.getTotpSecret());
        String verifyBody = mockMvc.perform(post("/v1/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new MfaVerifyRequest(mfaToken, code))))
                .andReturn().getResponse().getContentAsString();
        return tree(verifyBody).path("data");
    }

    protected String accessToken(String identifier, String password) throws Exception {
        return login(identifier, password).path("accessToken").asText();
    }

    protected String bearer(String identifier, String password) throws Exception {
        return "Bearer " + accessToken(identifier, password);
    }
}
