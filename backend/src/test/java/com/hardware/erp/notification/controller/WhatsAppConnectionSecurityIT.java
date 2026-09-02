package com.hardware.erp.notification.controller;

import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.notification.entity.TenantWhatsAppConnection;
import com.hardware.erp.notification.entity.WhatsAppConnectionStatus;
import com.hardware.erp.notification.repository.TenantWhatsAppConnectionRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CR-056 §20 - the spec's own six-test list for tenant-scoped WhatsApp
 * isolation. Connections are seeded directly through the repository
 * rather than via POST /connect, deliberately: connect() makes a real,
 * live call to Meta's Graph API to verify the credentials before saving
 * (TenantWhatsAppConnectionServiceImpl.verifyWithMeta) - exactly what
 * makes it trustworthy in production makes it unreachable from a
 * sandboxed test with no real Meta account behind it. What these tests
 * exercise instead - tenant resolution, the phone-number-id uniqueness
 * guard, and role permission gates - is the same isolation logic connect()
 * itself runs before it would ever reach Meta.
 */
class WhatsAppConnectionSecurityIT extends AbstractIntegrationTest {

    @Autowired private TenantWhatsAppConnectionRepository connectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private String owner() throws Exception {
        return bearer(OWNER_MOBILE, OWNER_PASSWORD);
    }

    private Long tenantAId() {
        return userRepository.findByIdentifier(OWNER_MOBILE).orElseThrow().getTenant().getId();
    }

    /**
     * Upsert, not a plain insert - AbstractIntegrationTest gives each test
     * method a shared, non-rolled-back database (see SupplierControllerIT's
     * own pattern of unique-per-test values rather than assuming isolation),
     * and tenant_whatsapp_connection has a UNIQUE(tenant_id) constraint, so
     * a second test seeding the same tenantAId would otherwise collide with
     * whatever an earlier test already left behind.
     */
    @Transactional
    TenantWhatsAppConnection seedConnection(Long tenantId, String phoneNumberId) {
        TenantWhatsAppConnection connection = connectionRepository.findByTenantId(tenantId)
                .orElseGet(() -> TenantWhatsAppConnection.builder()
                        .tenant(tenantRepository.getReferenceById(tenantId))
                        .connectedAt(LocalDateTime.now())
                        .build());
        connection.setBusinessAccountId("WABA-" + phoneNumberId);
        connection.setPhoneNumberId(phoneNumberId);
        // A real display_phone_number is short ("+91 90000 12345", ~16 chars) and
        // the column is sized for that (VARCHAR(20)) - deliberately not built from
        // phoneNumberId, which in this test is a longer synthetic id
        // ("PNI-TENANT-A-001") meant to exercise the real Meta-side opaque id
        // column (phone_number_id, VARCHAR(50)), not a human-readable display string.
        connection.setDisplayPhoneNumber("+91 90000 " + String.format("%05d", tenantId));
        connection.setBusinessName("Tenant " + tenantId + " Hardware");
        connection.setAccessToken("test-token-" + phoneNumberId);
        connection.setConnectionStatus(WhatsAppConnectionStatus.CONNECTED);
        connection.setLastVerifiedAt(LocalDateTime.now());
        connection.setDisconnectedAt(null);
        return connectionRepository.save(connection);
    }

    /** A brand-new tenant, registered through the real public endpoint - not the seeded one OWNER_MOBILE belongs to. */
    private String registerSecondTenantOwner(String mobile, String email) throws Exception {
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Second Test Hardware", "Second Owner", mobile, email,
                "Second@2026", null, true, "1.0", "1.0", false);
        mockMvc.perform(post("/v1/tenants/register").contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
        return bearer(mobile, "Second@2026");
    }

    @Test
    @DisplayName("Test 1/2 - each tenant sees only its own WhatsApp connection status")
    void eachTenantSeesOnlyItsOwnConnection() throws Exception {
        Long tenantAId = tenantAId();
        seedConnection(tenantAId, "PNI-TENANT-A-001");
        String tenantBOwner = registerSecondTenantOwner("9900011101", "second1@example.in");

        // Test 1: Tenant A -> Tenant A's own connection. PASS.
        mockMvc.perform(get("/v1/settings/whatsapp").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.businessName").value("Tenant " + tenantAId + " Hardware"));

        // Test 2: Tenant B -> never connected, never sees Tenant A's data. PASS.
        String tenantBBody = mockMvc.perform(get("/v1/settings/whatsapp").header("Authorization", tenantBOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(tenantBBody).doesNotContain("Tenant " + tenantAId + " Hardware");
    }

    @Test
    @DisplayName("Test 3 - a tenant cannot connect using a phone number id another tenant already owns")
    void cannotClaimAnotherTenantsPhoneNumber() throws Exception {
        Long tenantAId = tenantAId();
        seedConnection(tenantAId, "PNI-SHARED-002");
        String tenantBOwner = registerSecondTenantOwner("9900011102", "second2@example.in");

        String requestBody = json(new com.hardware.erp.notification.dto.WhatsAppConnectionRequest(
                "WABA-OTHER", "PNI-SHARED-002", "some-token"));

        mockMvc.perform(post("/v1/settings/whatsapp/connect").header("Authorization", tenantBOwner)
                        .contentType(APPLICATION_JSON).content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        // Tenant A's own connection is untouched by the rejected attempt.
        mockMvc.perform(get("/v1/settings/whatsapp").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    @DisplayName("Test 4 - disconnecting as Tenant B never touches Tenant A's connection")
    void disconnectIsTenantScoped() throws Exception {
        Long tenantAId = tenantAId();
        seedConnection(tenantAId, "PNI-TENANT-A-003");
        String tenantBOwner = registerSecondTenantOwner("9900011103", "second3@example.in");

        // Tenant B has nothing connected - disconnecting its own (absent) connection is a 404, never Tenant A's.
        mockMvc.perform(post("/v1/settings/whatsapp/disconnect").header("Authorization", tenantBOwner))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/settings/whatsapp").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    @DisplayName("Test 5 - STAFF (no SETTINGS_MANAGE) is forbidden from connecting or disconnecting WhatsApp")
    void staffCannotManageConnection() throws Exception {
        String staff = bearer(STAFF_MOBILE, STAFF_PASSWORD);
        String requestBody = json(new com.hardware.erp.notification.dto.WhatsAppConnectionRequest(
                "WABA-X", "PNI-STAFF-ATTEMPT", "some-token"));

        mockMvc.perform(post("/v1/settings/whatsapp/connect").header("Authorization", staff)
                        .contentType(APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/settings/whatsapp/disconnect").header("Authorization", staff))
                .andExpect(status().isForbidden());

        // STAFF also has no SETTINGS_VIEW - cannot even see the status.
        mockMvc.perform(get("/v1/settings/whatsapp").header("Authorization", staff))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER has SETTINGS_VIEW but not SETTINGS_MANAGE - can see status, cannot connect")
    void managerIsReadOnly() throws Exception {
        String manager = bearer(MANAGER_MOBILE, MANAGER_PASSWORD);

        mockMvc.perform(get("/v1/settings/whatsapp").header("Authorization", manager))
                .andExpect(status().isOk());

        String requestBody = json(new com.hardware.erp.notification.dto.WhatsAppConnectionRequest(
                "WABA-X", "PNI-MANAGER-ATTEMPT", "some-token"));
        mockMvc.perform(post("/v1/settings/whatsapp/connect").header("Authorization", manager)
                        .contentType(APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated request gives 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/v1/settings/whatsapp"))
                .andExpect(status().isUnauthorized());
    }
}
