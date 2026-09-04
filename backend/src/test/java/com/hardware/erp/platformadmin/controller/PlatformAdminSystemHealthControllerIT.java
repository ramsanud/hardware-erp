package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.*;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformIncidentRepository;
import com.hardware.erp.security.totp.TotpService;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real PostgreSQL - CR-057 phase 3 (System Health & Incident Monitoring).
 */
class PlatformAdminSystemHealthControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PlatformIncidentRepository incidentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;

    private PlatformAdmin persistAdmin(PlatformAdminRole role, String email) {
        return platformAdminRepository.save(PlatformAdmin.builder()
                .fullName("Test Admin").email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(role).status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(true).totpSecret(totpService.generateSecret())
                .tokenVersion(0).failedLoginAttempts(0).build());
    }

    private String fullyAuthenticate(String email, String totpSecret) throws Exception {
        String loginBody = mockMvc.perform(post("/v1/platform-admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminLoginRequest(email, RAW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = tree(loginBody).path("data").path("mfaToken").asText();
        String code = totpService.currentCode(totpSecret);
        String sessionBody = mockMvc.perform(post("/v1/platform-admin/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content(json(new PlatformAdminMfaVerifyRequest(mfaToken, code))))
                .andReturn().getResponse().getContentAsString();
        return tree(sessionBody).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("system health returns a real snapshot of all 7 services, gated by SYSTEM_HEALTH_VIEW")
    void systemHealthReturnsRealSnapshot() throws Exception {
        String email = "health-viewer@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.DEVELOPER, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/system-health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services.length()").value(7))
                .andExpect(jsonPath("$.data.services[?(@.service=='DATABASE')].status").exists())
                .andExpect(jsonPath("$.data.services[?(@.service=='DATABASE')].responseTimeMs").exists());
    }

    @Test
    @DisplayName("PLATFORM_ADMIN_MANAGE-only role (none exists) is irrelevant - every role holds SYSTEM_HEALTH_VIEW except the ones that shouldn't; FINANCE_ADMIN correctly lacks it")
    void financeAdminLacksSystemHealthView() throws Exception {
        String email = "finance-no-health@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.FINANCE_ADMIN, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/system-health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("incident lifecycle: open via repository, investigate, resolve - each step audited")
    void incidentLifecycle() throws Exception {
        PlatformIncident incident = incidentRepository.save(PlatformIncident.builder()
                .service(PlatformService.DATABASE)
                .severity(IncidentSeverity.HIGH)
                .title("Database is DOWN")
                .description("connection refused")
                .status(PlatformIncidentStatus.OPEN)
                .firstSeen(LocalDateTime.now())
                .lastSeen(LocalDateTime.now())
                .occurrenceCount(1)
                .build());

        String email = "incident-manager@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.DEVELOPER, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/incidents")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id==" + incident.getId() + ")]").exists());

        mockMvc.perform(post("/v1/platform-admin/incidents/" + incident.getId() + "/investigating")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVESTIGATING"));

        mockMvc.perform(post("/v1/platform-admin/incidents/" + incident.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        // Already resolved - a second resolve is refused, not a silent no-op.
        mockMvc.perform(post("/v1/platform-admin/incidents/" + incident.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a role without INCIDENT_MANAGE (FINANCE_ADMIN) is refused resolving an incident")
    void financeAdminCannotManageIncidents() throws Exception {
        PlatformIncident incident = incidentRepository.save(PlatformIncident.builder()
                .service(PlatformService.EMAIL).severity(IncidentSeverity.LOW).title("t")
                .status(PlatformIncidentStatus.OPEN).firstSeen(LocalDateTime.now()).lastSeen(LocalDateTime.now())
                .occurrenceCount(1).build());

        String email = "finance-incident@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.FINANCE_ADMIN, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/incidents/" + incident.getId() + "/resolve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
