package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.IncidentSeverity;
import com.hardware.erp.platformadmin.entity.PlatformIncidentStatus;
import com.hardware.erp.platformadmin.entity.PlatformService;

import java.time.LocalDateTime;

public record PlatformIncidentResponse(
        Long id,
        PlatformService service,
        IncidentSeverity severity,
        String title,
        String description,
        PlatformIncidentStatus status,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        Integer occurrenceCount,
        LocalDateTime resolvedAt,
        Long resolvedBy
) {}
