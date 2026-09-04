package com.hardware.erp.platformadmin.dto;

import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.entity.TenantExportStatus;

import java.time.LocalDateTime;

public record TenantExportLogResponse(
        Long id,
        TenantExportFormat format,
        TenantExportStatus status,
        Integer recordCount,
        Long fileSizeBytes,
        String errorDetail,
        LocalDateTime createdAt
) {}
