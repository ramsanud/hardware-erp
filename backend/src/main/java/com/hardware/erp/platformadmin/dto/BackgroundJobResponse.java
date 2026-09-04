package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.JobExecutionStatus;

import java.time.LocalDateTime;

public record BackgroundJobResponse(
        String jobName,
        JobExecutionStatus lastStatus,
        LocalDateTime lastRunAt,
        Long lastDurationMs,
        String lastDetail,
        /** Only the two real business jobs are retry-safe from this screen - see DeveloperToolsService. */
        boolean retryable
) {}
