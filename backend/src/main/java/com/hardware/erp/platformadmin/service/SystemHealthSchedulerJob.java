package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.dto.HealthStatus;
import com.hardware.erp.platformadmin.entity.IncidentSeverity;
import com.hardware.erp.platformadmin.entity.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every 5 minutes so the System Health page's "last checked / last
 * failure / error count" fields are real history, not just "computed the
 * instant you loaded this page." Each service's result is recorded to
 * job_execution_log under job name "health:&lt;service&gt;" (lower-cased) -
 * see JobExecutionTracker/JobExecutionLogRepository. DOWN/DEGRADED opens or
 * bumps a PlatformIncident; a return to HEALTHY auto-resolves one if open.
 */
@Component
@RequiredArgsConstructor
public class SystemHealthSchedulerJob {

    private final SystemHealthCheckService healthCheckService;
    private final JobExecutionTracker jobExecutionTracker;
    private final PlatformIncidentService incidentService;

    @Scheduled(fixedRateString = "${app.system-health.check-interval-ms:300000}")
    public void runChecks() {
        for (PlatformService service : PlatformService.values()) {
            String jobName = "health:" + service.name().toLowerCase();
            Long runId = jobExecutionTracker.start(jobName);
            var result = healthCheckService.checkOne(service);

            if (result.status() == HealthStatus.HEALTHY || result.status() == HealthStatus.UNKNOWN) {
                jobExecutionTracker.success(runId, result.detail());
                incidentService.autoResolveIfOpen(service);
            } else {
                jobExecutionTracker.failure(runId, result.detail());
                IncidentSeverity severity = result.status() == HealthStatus.DOWN
                        ? IncidentSeverity.HIGH : IncidentSeverity.MEDIUM;
                incidentService.recordFailure(service, severity,
                        "%s is %s".formatted(service.name(), result.status()), result.detail());
            }
        }
    }
}
