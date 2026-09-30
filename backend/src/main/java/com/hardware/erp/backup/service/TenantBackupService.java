package com.hardware.erp.backup.service;

import com.hardware.erp.backup.entity.TenantBackup;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CR-092. The shop's own backups (BACKUP_MANAGE). Taking one on demand is
 * available to every plan under DATA_EXPORT; the nightly automatic one
 * needs AUTO_BACKUP (PREMIUM). Never a platform-admin concern: everything
 * here is tenant-scoped from the JWT.
 */
public interface TenantBackupService {

    record BackupSummary(Long id, TenantExportFormat format, TenantBackup.BackupTrigger triggerType,
                         TenantBackup.BackupStatus status, Integer recordCount, Long fileSizeBytes,
                         String errorDetail, LocalDateTime createdAt) {}

    record BackupFile(String fileName, String contentType, byte[] body) {}

    List<BackupSummary> history();

    BackupSummary takeNow(TenantExportFormat format);

    BackupFile download(Long id);

    /** Called by the nightly job for one tenant, in its own transaction. Prunes to the newest 7 afterwards. */
    void scheduled(Long tenantId);
}
