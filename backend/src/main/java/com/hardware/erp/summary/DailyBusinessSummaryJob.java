package com.hardware.erp.summary;

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
 * CR-092. 20:30 IST every day - after the shop has closed, before the
 * owner has stopped looking at the phone. One tenant per transaction
 * (DailyBusinessSummaryService), so one shop's failure never blocks the
 * rest; runs are recorded through JobExecutionTracker like every other
 * job, so the platform health page can see it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyBusinessSummaryJob {

    static final String JOB_NAME = "DAILY_BUSINESS_SUMMARY";
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final DailyBusinessSummaryService summaryService;
    private final JobExecutionTracker jobExecutionTracker;

    @Scheduled(cron = "${app.daily-summary.cron:0 30 20 * * *}", zone = "Asia/Kolkata")
    public void run() {
        Long runId = jobExecutionTracker.start(JOB_NAME);
        int ok = 0;
        int failed = 0;
        try {
            List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
            LocalDate today = LocalDate.now(SHOP_ZONE);
            for (Tenant tenant : tenants) {
                try {
                    summaryService.summarise(tenant.getId(), today);
                    ok++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Daily summary failed for tenant {}", tenant.getId(), ex);
                }
            }
            jobExecutionTracker.success(runId, ok + " tenants summarised, " + failed + " failed");
        } catch (RuntimeException ex) {
            jobExecutionTracker.failure(runId, ex.getMessage());
            throw ex;
        }
    }
}
