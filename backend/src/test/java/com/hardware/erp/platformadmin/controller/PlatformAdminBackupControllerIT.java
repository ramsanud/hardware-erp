package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.platformadmin.dto.PlatformAdminLoginRequest;
import com.hardware.erp.platformadmin.dto.PlatformAdminMfaVerifyRequest;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAdminRole;
import com.hardware.erp.platformadmin.entity.PlatformAdminStatus;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.entity.PlatformAuditLog;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.platformadmin.repository.PlatformAuditLogRepository;
import com.hardware.erp.security.totp.TotpService;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL, real filter chain - CR-059 (tenant export audit).
 *
 * The point of this class is the audit evidence, not the file: exporting an
 * entire tenant's customer/supplier/invoice dataset previously wrote nothing
 * to platform_audit_log at all. A mocked unit test cannot prove that a
 * REQUIRES_NEW audit row actually survives the rollback of the caller's own
 * transaction - only a real database can, which is what
 * unknownTenantLeavesAuditEvidenceEvenThoughTheTransactionRolledBack does.
 */
class PlatformAdminBackupControllerIT extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SuperSecret@2026";
    private static final long SEEDED_TENANT_ID = 1L;

    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private PlatformAuditLogRepository platformAuditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;

    private PlatformAdmin persistAdmin(PlatformAdminRole role, String email) {
        return platformAdminRepository.save(PlatformAdmin.builder()
                .fullName("Test Admin")
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .role(role)
                .status(PlatformAdminStatus.ACTIVE)
                .mfaEnabled(true)
                .totpSecret(totpService.generateSecret())
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .build());
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

    private List<PlatformAuditLog> auditRows(PlatformAuditAction action, Long adminId) {
        return platformAuditLogRepository
                .search(adminId, action, null, "TENANT", null, null, PageRequest.of(0, 50))
                .getContent();
    }

    @Test
    @DisplayName("a real export is audited REQUESTED then COMPLETED, against the tenant and the acting admin, with the caller's IP")
    void exportIsAuditedRequestedThenCompleted() throws Exception {
        String email = "backup-exporter@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/tenants/" + SEEDED_TENANT_ID + "/backups?format=JSON")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<PlatformAuditLog> requested = auditRows(PlatformAuditAction.TENANT_EXPORT_REQUESTED, admin.getId());
        assertThat(requested).hasSize(1);
        assertThat(requested.get(0).getTargetId()).isEqualTo(SEEDED_TENANT_ID);
        assertThat(requested.get(0).isSuccess()).isTrue();
        assertThat(requested.get(0).getDetail()).isEqualTo("JSON");
        // Proves the HttpServletRequest is genuinely threaded through to the
        // audit row, not dropped the way the service's old signature forced.
        assertThat(requested.get(0).getIpAddress()).isNotBlank();

        List<PlatformAuditLog> completed = auditRows(PlatformAuditAction.TENANT_EXPORT_COMPLETED, admin.getId());
        assertThat(completed).hasSize(1);
        assertThat(completed.get(0).getTargetId()).isEqualTo(SEEDED_TENANT_ID);
        assertThat(completed.get(0).isSuccess()).isTrue();
        // Volume metadata only - the seed tenant's real customer names must not be here.
        assertThat(completed.get(0).getDetail()).matches("JSON, \\d+ records, \\d+ bytes");
    }

    @Test
    @DisplayName("an export against an unknown tenant still leaves REQUESTED + FAILED evidence, even though its transaction rolled back")
    void unknownTenantLeavesAuditEvidenceEvenThoughTheTransactionRolledBack() throws Exception {
        String email = "backup-prober@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.PLATFORM_ADMIN, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(post("/v1/platform-admin/tenants/999999/backups?format=CSV")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        List<PlatformAuditLog> requested = auditRows(PlatformAuditAction.TENANT_EXPORT_REQUESTED, admin.getId());
        assertThat(requested).hasSize(1);
        assertThat(requested.get(0).getTargetId()).isEqualTo(999999L);

        List<PlatformAuditLog> failed = auditRows(PlatformAuditAction.TENANT_EXPORT_FAILED, admin.getId());
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).isSuccess()).isFalse();
        assertThat(failed.get(0).getDetail()).isEqualTo("Tenant not found.");
    }

    @Test
    @DisplayName("READ_ONLY_AUDITOR can read export history but is refused the export itself - and a refused attempt writes no audit row")
    void readOnlyAuditorCannotExport() throws Exception {
        String email = "backup-auditor@platform.test";
        PlatformAdmin admin = persistAdmin(PlatformAdminRole.READ_ONLY_AUDITOR, email);
        String token = fullyAuthenticate(email, admin.getTotpSecret());

        mockMvc.perform(get("/v1/platform-admin/tenants/" + SEEDED_TENANT_ID + "/backups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/platform-admin/tenants/" + SEEDED_TENANT_ID + "/backups?format=JSON")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // @PreAuthorize rejects before the service runs, so there is nothing to
        // audit here - the refusal itself is a Spring Security concern, not an
        // export lifecycle event. Asserted so a future change that starts
        // writing a REQUESTED row for a refused caller is caught deliberately.
        assertThat(auditRows(PlatformAuditAction.TENANT_EXPORT_REQUESTED, admin.getId())).isEmpty();
    }
}
