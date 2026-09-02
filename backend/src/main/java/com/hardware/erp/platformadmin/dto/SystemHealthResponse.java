package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.PlatformService;

import java.time.LocalDateTime;
import java.util.List;

public record SystemHealthResponse(
        List<ServiceHealth> services,
        LocalDateTime generatedAt
) {
    public record ServiceHealth(
            PlatformService service,
            HealthStatus status,
            /** Null when this service's check does not measure a duration (e.g. WhatsApp/Email, which read stored state). */
            Long responseTimeMs,
            String detail,
            /** Null when the scheduled health-check job has not run yet since this server started. */
            LocalDateTime lastCheckedAt,
            LocalDateTime lastFailureAt,
            /** FAILED runs in the last 24 hours. */
            long errorCount
    ) {}
}
