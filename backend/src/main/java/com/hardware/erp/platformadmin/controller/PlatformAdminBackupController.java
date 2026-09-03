package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.TenantExportLogResponse;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.security.PlatformAdminPrincipal;
import com.hardware.erp.platformadmin.service.TenantDataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CR-057 phase 11 - Backup Center's real, buildable half: on-demand tenant
 * data export, logged. See TenantDataExportService's own javadoc for why
 * this is deliberately not presented as an automated backup.
 * BACKUP_VIEW/BACKUP_MANAGE enforced here, not just hidden in the frontend.
 */
@RestController
@RequestMapping("/v1/platform-admin/tenants/{tenantId}/backups")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Backup Center", description = "On-demand tenant data export, JSON or CSV, logged")
public class PlatformAdminBackupController {

    private final TenantDataExportService exportService;

    @GetMapping
    @PreAuthorize("hasAuthority('BACKUP_VIEW')")
    @Operation(summary = "Export history for a tenant - who exported what, when, and whether it succeeded")
    public ResponseEntity<ApiResponse<List<TenantExportLogResponse>>> history(@PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(exportService.history(tenantId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BACKUP_MANAGE')")
    @Operation(summary = "Export a tenant's core business data as JSON or a CSV zip - generated fresh, never stored")
    public ResponseEntity<byte[]> export(@PathVariable Long tenantId, @RequestParam("format") TenantExportFormat format) {
        byte[] body = exportService.export(tenantId, format, currentAdminId());
        MediaType contentType = format == TenantExportFormat.JSON
                ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("application/zip");
        String filename = "tenant-" + tenantId + "-export." + (format == TenantExportFormat.JSON ? "json" : "zip");
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    private Long currentAdminId() {
        var principal = (PlatformAdminPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.getId();
    }
}
