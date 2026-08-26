package com.hardware.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.auth.dto.LoginRequest;
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

    /** Full login, returning the parsed data node so tests can pull either token. */
    protected JsonNode login(String identifier, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(identifier, password))))
                .andReturn().getResponse().getContentAsString();
        return tree(body).path("data");
    }

    protected String accessToken(String identifier, String password) throws Exception {
        return login(identifier, password).path("accessToken").asText();
    }

    protected String bearer(String identifier, String password) throws Exception {
        return "Bearer " + accessToken(identifier, password);
    }
}
