package com.hardware.erp.backup.service.impl;

import com.hardware.erp.backup.entity.TenantBackup;
import com.hardware.erp.backup.entity.TenantBackup.BackupStatus;
import com.hardware.erp.backup.entity.TenantBackup.BackupTrigger;
import com.hardware.erp.backup.repository.TenantBackupRepository;
import com.hardware.erp.backup.service.TenantBackupService;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.service.TenantDataExportService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBackupServiceImpl implements TenantBackupService {

    static final int KEEP = 7;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final TenantBackupRepository backupRepository;
    private final TenantDataExportService exportService;
    private final FeatureAccessService featureAccessService;
    private final ActivityLogService activityLog;

    @Override
    @Transactional(readOnly = true)
    public List<BackupSummary> history() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return backupRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::summary).toList();
    }

    @Override
    @Transactional
    public BackupSummary takeNow(TenantExportFormat format) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.DATA_EXPORT);
        TenantBackup saved = take(tenantId, format == null ? TenantExportFormat.JSON : format, BackupTrigger.MANUAL);
        activityLog.action("SETTINGS", "TENANT_BACKUP", saved.getId(), saved.getFormat().name(), ActivityAction.EXPORT,
                saved.getStatus() == BackupStatus.COMPLETED
                        ? saved.getRecordCount() + " records, " + saved.getFileSizeBytes() + " bytes"
                        : "failed: " + saved.getErrorDetail());
        if (saved.getStatus() == BackupStatus.FAILED) {
            throw new BusinessException("Could not build the backup. This has been logged.",
                    HttpStatus.INTERNAL_SERVER_ERROR, "BACKUP_FAILED");
        }
        return summary(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BackupFile download(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantBackup backup = backupRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Backup", id));
        if (backup.getStatus() != BackupStatus.COMPLETED || backup.getFileData() == null) {
            throw new BusinessException("This backup did not complete and has no file", HttpStatus.UNPROCESSABLE_ENTITY, "BACKUP_INCOMPLETE");
        }
        boolean json = backup.getFormat() == TenantExportFormat.JSON;
        String name = "backup-" + backup.getCreatedAt().format(STAMP) + (json ? ".json" : ".zip");
        return new BackupFile(name, json ? "application/json" : "application/zip", backup.getFileData());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduled(Long tenantId) {
        if (!featureAccessService.hasFeature(tenantId, FeatureKey.AUTO_BACKUP)) {
            return;
        }
        take(tenantId, TenantExportFormat.JSON, BackupTrigger.SCHEDULED);
        int pruned = backupRepository.pruneOlderThanNewest(tenantId, KEEP);
        if (pruned > 0) {
            log.info("Pruned {} old backup(s) for tenant {}", pruned, tenantId);
        }
    }

    private TenantBackup take(Long tenantId, TenantExportFormat format, BackupTrigger trigger) {
        TenantBackup backup = TenantBackup.builder().tenantId(tenantId).format(format).triggerType(trigger).build();
        try {
            TenantDataExportService.Snapshot snapshot = exportService.buildSnapshot(tenantId, format);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setRecordCount(snapshot.recordCount());
            backup.setFileSizeBytes((long) snapshot.body().length);
            backup.setFileData(snapshot.body());
        } catch (Exception e) {
            log.error("Backup failed for tenant {}", tenantId, e);
            backup.setStatus(BackupStatus.FAILED);
            backup.setErrorDetail(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        return backupRepository.save(backup);
    }

    private BackupSummary summary(TenantBackup b) {
        return new BackupSummary(b.getId(), b.getFormat(), b.getTriggerType(), b.getStatus(), b.getRecordCount(),
                b.getFileSizeBytes(), b.getErrorDetail(), b.getCreatedAt());
    }
}
