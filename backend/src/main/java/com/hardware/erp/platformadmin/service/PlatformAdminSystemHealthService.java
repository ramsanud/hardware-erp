package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.dto.SystemHealthResponse;
import com.hardware.erp.platformadmin.entity.JobExecutionLog;
import com.hardware.erp.platformadmin.entity.JobExecutionStatus;
import com.hardware.erp.platformadmin.repository.JobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Combines SystemHealthCheckService's live-computed status with
 * job_execution_log's history (from SystemHealthSchedulerJob's runs) for
 * the "last checked / last failure / error count" fields the spec asks
 * for. The status shown is always the fresh live check, not a stale
 * cached one - history only supplies the "when did this last happen"
 * fields a single live check cannot answer.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminSystemHealthService {

    private final SystemHealthCheckService healthCheckService;
    private final JobExecutionLogRepository jobExecutionLogRepository;

    @Transactional(readOnly = true)
    public SystemHealthResponse overview() {
        List<SystemHealthResponse.ServiceHealth> services = healthCheckService.checkAll().values().stream()
                .map(this::enrich)
                .toList();
        return new SystemHealthResponse(services, LocalDateTime.now());
    }

    private SystemHealthResponse.ServiceHealth enrich(SystemHealthCheckService.CheckResult live) {
        String jobName = "health:" + live.service().name().toLowerCase();
        LocalDateTime lastCheckedAt = jobExecutionLogRepository.findFirstByJobNameOrderByStartedAtDesc(jobName)
                .map(JobExecutionLog::getStartedAt).orElse(null);
        LocalDateTime lastFailureAt = jobExecutionLogRepository
                .findFirstByJobNameAndStatusOrderByStartedAtDesc(jobName, JobExecutionStatus.FAILED)
                .map(JobExecutionLog::getStartedAt).orElse(null);
        long errorCount = jobExecutionLogRepository.countByJobNameAndStatusAndStartedAtAfter(
                jobName, JobExecutionStatus.FAILED, LocalDateTime.now().minusHours(24));

        return new SystemHealthResponse.ServiceHealth(
                live.service(), live.status(), live.responseTimeMs(), live.detail(),
                lastCheckedAt, lastFailureAt, errorCount);
    }
}
