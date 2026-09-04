package com.hardware.erp.platformadmin.service;

import com.hardware.erp.auth.service.impl.TokenCleanupJob;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.notification.reminder.ReminderSchedulerService;
import com.hardware.erp.platformadmin.dto.BackgroundJobResponse;
import com.hardware.erp.platformadmin.dto.DatabaseDiagnosticsResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.JobExecutionLogRepository;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

/**
 * Phase 7 - safe, real diagnostics only. No arbitrary SQL execution
 * endpoint exists anywhere in this service, deliberately (spec's own
 * "DO NOT create unrestricted production SQL execution" instruction) -
 * every number here comes from a predefined, safe introspection call
 * (Hikari's own pool MXBean, Flyway's own MigrationInfoService, this
 * app's own job_execution_log).
 */
@Service
@RequiredArgsConstructor
public class DeveloperToolsService {

    /** The only jobs this screen can safely re-trigger on demand - both are naturally idempotent (re-running early changes nothing). */
    private static final Set<String> RETRYABLE_JOBS = Set.of(TokenCleanupJob.JOB_NAME, ReminderSchedulerService.JOB_NAME);

    @PersistenceContext
    private EntityManager entityManager;

    private final DataSource dataSource;
    private final Flyway flyway;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final TokenCleanupJob tokenCleanupJob;
    private final ReminderSchedulerService reminderSchedulerService;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<BackgroundJobResponse> listJobs() {
        return jobExecutionLogRepository.findDistinctJobNames().stream()
                .map(jobName -> {
                    var latest = jobExecutionLogRepository.findFirstByJobNameOrderByStartedAtDesc(jobName).orElseThrow();
                    return new BackgroundJobResponse(
                            jobName, latest.getStatus(), latest.getStartedAt(), latest.getDurationMs(),
                            latest.getDetail(), RETRYABLE_JOBS.contains(jobName));
                })
                .toList();
    }

    @Transactional
    public void retryJob(String jobName, Long actingAdminId, HttpServletRequest request) {
        if (!RETRYABLE_JOBS.contains(jobName)) {
            throw new BusinessException("This job cannot be retried from here.");
        }
        if (TokenCleanupJob.JOB_NAME.equals(jobName)) {
            tokenCleanupJob.purge();
        } else {
            reminderSchedulerService.sendDailyReminders();
        }
        PlatformAdmin admin = platformAdminRepository.getReferenceById(actingAdminId);
        auditService.record(PlatformAuditAction.JOB_RETRIED, admin, true, "JOB", null, jobName, request);
    }

    public DatabaseDiagnosticsResponse databaseDiagnostics() {
        boolean reachable;
        long pingMs;
        long start = System.nanoTime();
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            reachable = true;
        } catch (Exception ex) {
            reachable = false;
        }
        pingMs = (System.nanoTime() - start) / 1_000_000;

        DatabaseDiagnosticsResponse.PoolStatus pool = null;
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean mxBean = hikari.getHikariPoolMXBean();
            if (mxBean != null) {
                pool = new DatabaseDiagnosticsResponse.PoolStatus(
                        mxBean.getActiveConnections(), mxBean.getIdleConnections(),
                        mxBean.getTotalConnections(), hikari.getMaximumPoolSize());
            }
        }

        MigrationInfo current = flyway.info().current();
        boolean pending = flyway.info().pending().length > 0;
        int appliedCount = flyway.info().applied().length;

        return new DatabaseDiagnosticsResponse(
                reachable, pingMs, pool,
                current == null ? null : current.getVersion().toString(),
                appliedCount, pending);
    }
}
