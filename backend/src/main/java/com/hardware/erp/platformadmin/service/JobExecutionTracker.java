package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.entity.JobExecutionLog;
import com.hardware.erp.platformadmin.entity.JobExecutionStatus;
import com.hardware.erp.platformadmin.repository.JobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Wraps one execution of a scheduled job or a system health check so
 * Developer Tools / System Health can show real history, never an assumed
 * "it probably ran". REQUIRES_NEW so a failed job still gets its FAILED row
 * recorded even though the job's own transaction (if any) rolls back -
 * same reasoning as PlatformAuditService.record().
 *
 * Usage:
 *   long runId = tracker.start("token-cleanup");
 *   try {
 *       ... do the work ...
 *       tracker.success(runId, "removed 12 rows");
 *   } catch (Exception ex) {
 *       tracker.failure(runId, ex.getMessage());
 *       throw ex;
 *   }
 */
@Service
@RequiredArgsConstructor
public class JobExecutionTracker {

    private final JobExecutionLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String jobName) {
        JobExecutionLog log = repository.save(JobExecutionLog.builder()
                .jobName(jobName)
                .startedAt(LocalDateTime.now())
                .status(JobExecutionStatus.RUNNING)
                .build());
        return log.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(Long runId, String detail) {
        finish(runId, JobExecutionStatus.SUCCESS, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(Long runId, String detail) {
        finish(runId, JobExecutionStatus.FAILED, detail);
    }

    private void finish(Long runId, JobExecutionStatus status, String detail) {
        repository.findById(runId).ifPresent(log -> {
            LocalDateTime finishedAt = LocalDateTime.now();
            log.setFinishedAt(finishedAt);
            log.setStatus(status);
            log.setDetail(detail);
            log.setDurationMs(ChronoUnit.MILLIS.between(log.getStartedAt(), finishedAt));
            repository.save(log);
        });
    }
}
