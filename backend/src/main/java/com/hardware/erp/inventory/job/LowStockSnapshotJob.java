package com.hardware.erp.inventory.job;

import com.hardware.erp.inventory.repository.LowStockSnapshotRepository;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.platformadmin.service.JobExecutionTracker;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * CR-084. Records, once a day for every live shop, how many products are
 * at or below their reorder level - the one dashboard figure that had no
 * history and therefore no honest sparkline.
 *
 * Runs at 00:15 IST so the row belongs unambiguously to the new day, and
 * uses the same {@code countLowStock} the Stock list's filter and the
 * reminder job use, so the three can never disagree about what "low" means.
 *
 * Per-tenant work commits on its own: the transaction boundary is the
 * repository's upsert, not a method on this class - a {@code REQUIRES_NEW}
 * here would be self-invoked from the loop and never cross the proxy, which
 * is precisely BUG-BE-002. One shop's failure is logged and the loop
 * continues, the same rule {@code ReminderSchedulerService} follows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockSnapshotJob {

    public static final String JOB_NAME = "low-stock-snapshot";
    /** The calendar every snapshot belongs to - a shop in India, whatever zone the server runs in. */
    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final StockRepository stockRepository;
    private final LowStockSnapshotRepository snapshotRepository;
    private final JobExecutionTracker jobExecutionTracker;

    @Scheduled(cron = "${app.low-stock-snapshot.cron:0 15 0 * * *}", zone = "Asia/Kolkata")
    public void takeDailySnapshots() {
        Long runId = jobExecutionTracker.start(JOB_NAME);
        int ok = 0;
        int failed = 0;
        try {
            List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
            LocalDate today = LocalDate.now(SHOP_ZONE);
            for (Tenant tenant : tenants) {
                try {
                    snapshot(tenant.getId(), today);
                    ok++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Low-stock snapshot failed for tenant {}", tenant.getId(), ex);
                }
            }
            jobExecutionTracker.success(runId, ok + " tenants snapshotted, " + failed + " failed");
        } catch (RuntimeException ex) {
            jobExecutionTracker.failure(runId, ex.getMessage());
            throw ex;
        }
    }

    /**
     * One tenant, one day, its own transaction. Also called by the analytics
     * service when a shop reads its trend and has no row for today yet, so a
     * fresh deploy shows a real point immediately instead of waiting for
     * midnight. Idempotent - the repository upserts on (tenant, day).
     */
    public int snapshot(Long tenantId, LocalDate day) {
        int count = (int) stockRepository.countLowStock(tenantId);
        snapshotRepository.upsert(tenantId, day, count);
        return count;
    }
}
