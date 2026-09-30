package com.hardware.erp.backup.controller;

import com.hardware.erp.backup.service.TenantBackupService;
import com.hardware.erp.backup.service.TenantBackupService.BackupFile;
import com.hardware.erp.backup.service.TenantBackupService.BackupSummary;
import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CR-092. BACKUP_MANAGE throughout - owner-only, like DATA_RESET. */
@RestController
@RequestMapping("/v1/backups")
@RequiredArgsConstructor
@Tag(name = "Backups")
public class TenantBackupController {

    private final TenantBackupService backupService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BACKUP_MANAGE)")
    public ApiResponse<List<BackupSummary>> history() {
        return ApiResponse.ok(backupService.history());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BACKUP_MANAGE)")
    public ApiResponse<BackupSummary> takeNow(@RequestParam(defaultValue = "JSON") TenantExportFormat format) {
        return ApiResponse.ok("Backup taken", backupService.takeNow(format));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).BACKUP_MANAGE)")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        BackupFile file = backupService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("Content-Disposition", "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.body());
    }
}
