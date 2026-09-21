package com.hardware.erp.document.job;

import com.hardware.erp.document.repository.ReportJobRepository;
import com.hardware.erp.platformadmin.service.JobExecutionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CR-101. A finished report_job row carries its file as BYTEA (V62) - kept
 * for exactly as long as a shop plausibly still wants the download link,
 * not forever. Runs once a day, across every tenant at once (a single
 * DELETE ... WHERE created_at < cutoff, not a per-tenant loop): unlike
 * LowStockSnapshotJob this has no per-tenant computation to isolate, only
 * one predicate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportJobCleanupJob {

    public static final String JOB_NAME = "report-job-cleanup";
    private static final int RETENTION_DAYS = 7;

    private final ReportJobRepository reportJobRepository;
    private final JobExecutionTracker jobExecutionTracker;

    @Scheduled(cron = "${app.report-job-cleanup.cron:0 30 0 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void purgeExpiredJobs() {
        Long runId = jobExecutionTracker.start(JOB_NAME);
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
            int deleted = reportJobRepository.deleteByCreatedAtBefore(cutoff);
            jobExecutionTracker.success(runId, deleted + " report jobs older than " + RETENTION_DAYS + " days purged");
        } catch (RuntimeException ex) {
            jobExecutionTracker.failure(runId, ex.getMessage());
            throw ex;
        }
    }
}
