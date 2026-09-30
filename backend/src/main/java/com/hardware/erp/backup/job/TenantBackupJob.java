package com.hardware.erp.backup.job;

import com.hardware.erp.backup.service.TenantBackupService;
import com.hardware.erp.platformadmin.service.JobExecutionTracker;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CR-092. 01:30 IST, after the low-stock snapshot (00:15) and well before
 * opening time. Only tenants whose plan has AUTO_BACKUP are backed up -
 * the service checks per tenant, so a downgrade takes effect the next
 * night with nothing to reconfigure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBackupJob {

    static final String JOB_NAME = "TENANT_AUTO_BACKUP";

    private final TenantRepository tenantRepository;
    private final TenantBackupService backupService;
    private final JobExecutionTracker jobExecutionTracker;

    @Scheduled(cron = "${app.auto-backup.cron:0 30 1 * * *}", zone = "Asia/Kolkata")
    public void run() {
        Long runId = jobExecutionTracker.start(JOB_NAME);
        int ok = 0;
        int failed = 0;
        try {
            List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
            for (Tenant tenant : tenants) {
                try {
                    backupService.scheduled(tenant.getId());
                    ok++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Automatic backup failed for tenant {}", tenant.getId(), ex);
                }
            }
            jobExecutionTracker.success(runId, ok + " tenants processed, " + failed + " failed");
        } catch (RuntimeException ex) {
            jobExecutionTracker.failure(runId, ex.getMessage());
            throw ex;
        }
    }
}
