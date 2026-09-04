package com.hardware.erp.supportticket.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
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
 * Real PostgreSQL - CR-057 phase 4 (Support Center), the tenant-facing and
 * platform-admin sides exercised together end to end: a tenant raises a
 * ticket, a platform admin replies with an internal note the tenant must
 * never see, then resolves it, then the tenant replies again and the
 * ticket must reopen automatically.
 */
class SupportTicketFlowIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PlatformAuditLogRepository platformAuditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;
    @Autowired private com.hardware.erp.tenant.repository.TenantRepository tenantRepository;
    @Autowired private com.hardware.erp.auth.repository.UserRepository userRepository;
    @Autowired private com.hardware.erp.auth.repository.RoleRepository roleRepository;

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
    @DisplayName("full ticket lifecycle: tenant creates, admin replies with an internal note the tenant never sees, admin resolves, tenant reply reopens it")
    void fullTicketLifecycle() throws Exception {
        String tenantAuth = bearer(OWNER_MOBILE, OWNER_PASSWORD);

        String createBody = mockMvc.perform(post("/v1/support-tickets")
                        .header("Authorization", tenantAuth)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"subject":"Cannot generate invoice PDF","description":"Button does nothing","category":"INVOICE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        long ticketId = tree(createBody).path("data").path("id").asLong();

        // Tenant sees it in their own list.
        mockMvc.perform(get("/v1/support-tickets").header("Authorization", tenantAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id==" + ticketId + ")]").exists());

        String platformToken = platformAdminToken(PlatformAdminRole.SUPPORT_ADMIN, "support-flow@platform.test");

        // Admin sees it cross-tenant with the real tenant name attached.
        mockMvc.perform(get("/v1/platform-admin/support/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantName").value("Default Shop"))
                .andExpect(jsonPath("$.data.raisedByName").value("Saravanan Murugan"));

        // Admin adds an internal note - not customer visible.
        mockMvc.perform(post("/v1/platform-admin/support/tickets/" + ticketId + "/messages")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"message":"Checked logs - PDF service threw a NPE, escalating","internal":true}
                                """))
                .andExpect(status().isOk());

        // Admin also sends a real customer-visible reply.
        mockMvc.perform(post("/v1/platform-admin/support/tickets/" + ticketId + "/messages")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"message":"We are looking into this, will update shortly","internal":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_USER"));

        // The tenant's own view must show the public reply but NEVER the internal note.
        String tenantDetailBody = mockMvc.perform(get("/v1/support-tickets/" + ticketId)
                        .header("Authorization", tenantAuth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tenantMessages = tree(tenantDetailBody).path("data").path("messages");
        assertThat(tenantMessages).hasSize(1);
        assertThat(tenantMessages.get(0).path("message").asText()).contains("looking into this");

        // The admin's own view sees both.
        String adminDetailBody = mockMvc.perform(get("/v1/platform-admin/support/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + platformToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(tree(adminDetailBody).path("data").path("messages")).hasSize(2);

        // Admin resolves it.
        mockMvc.perform(post("/v1/platform-admin/support/tickets/" + ticketId + "/status/RESOLVED")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // Tenant replies to a resolved ticket - it must reopen, not stay resolved.
        mockMvc.perform(post("/v1/support-tickets/" + ticketId + "/messages")
                        .header("Authorization", tenantAuth)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"message":"Still broken for me, please reopen","internal":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        // Every privileged admin action left an audit trail under the real acting admin.
        boolean repliedAudited = platformAuditLogRepository.findAll().stream()
                .anyMatch(e -> e.getAction() == PlatformAuditAction.SUPPORT_REPLIED
                        && Long.valueOf(ticketId).equals(e.getTargetId()));
        boolean internalNoteAudited = platformAuditLogRepository.findAll().stream()
                .anyMatch(e -> e.getAction() == PlatformAuditAction.SUPPORT_INTERNAL_NOTE_ADDED
                        && Long.valueOf(ticketId).equals(e.getTargetId()));
        boolean statusAudited = platformAuditLogRepository.findAll().stream()
                .anyMatch(e -> e.getAction() == PlatformAuditAction.SUPPORT_STATUS_CHANGED
                        && Long.valueOf(ticketId).equals(e.getTargetId()));
        assertThat(repliedAudited).isTrue();
        assertThat(internalNoteAudited).isTrue();
        assertThat(statusAudited).isTrue();
    }

    @Test
    @DisplayName("a tenant cannot read another tenant's support ticket by guessing its id")
    void tenantIsolation() throws Exception {
        com.hardware.erp.tenant.entity.Tenant otherTenant = tenantRepository.save(
                com.hardware.erp.tenant.entity.Tenant.builder()
                        .slug("isolation-target-" + System.nanoTime())
                        .name("Isolation Target Co")
                        .status(com.hardware.erp.tenant.entity.TenantStatus.ACTIVE)
                        .build());
        var otherRole = roleRepository.save(com.hardware.erp.auth.entity.Role.builder()
                .tenant(otherTenant).code("OWNER").name("Owner")
                .status(com.hardware.erp.auth.entity.RoleStatus.ACTIVE).build());
        userRepository.save(com.hardware.erp.auth.entity.User.builder()
                .tenant(otherTenant).role(otherRole).fullName("Other Owner")
                .mobileNo("9700099901").email("other-owner-support@example.test")
                .passwordHash(passwordEncoder.encode(OWNER_PASSWORD))
                .status(com.hardware.erp.auth.entity.UserStatus.ACTIVE).build());

        String ownerAuth = bearer(OWNER_MOBILE, OWNER_PASSWORD);
        String createBody = mockMvc.perform(post("/v1/support-tickets")
                        .header("Authorization", ownerAuth)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"subject":"Isolation test","description":"desc","category":"OTHER"}
                                """))
                .andReturn().getResponse().getContentAsString();
        long ticketId = tree(createBody).path("data").path("id").asLong();

        String otherOwnerAuth = bearer("9700099901", OWNER_PASSWORD);
        mockMvc.perform(get("/v1/support-tickets/" + ticketId).header("Authorization", otherOwnerAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a platform-admin role without SUPPORT_VIEW is refused reading tickets")
    void roleWithoutSupportViewIsRefused() throws Exception {
        String token = platformAdminToken(PlatformAdminRole.FINANCE_ADMIN, "finance-no-support@platform.test");

        mockMvc.perform(get("/v1/platform-admin/support/tickets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
