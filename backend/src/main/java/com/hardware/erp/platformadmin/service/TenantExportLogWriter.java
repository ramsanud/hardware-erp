package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.entity.PlatformTenantExport;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import com.hardware.erp.platformadmin.entity.TenantExportStatus;
import com.hardware.erp.platformadmin.repository.PlatformTenantExportRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes platform_tenant_export rows in their own transaction, exactly like
 * JobExecutionTracker does for job runs and PlatformAuditService does for
 * audit rows.
 *
 * CR-059, BUG-PA-001: before this existed, TenantDataExportServiceImpl saved
 * its FAILED row inside its own @Transactional method and then threw a
 * BusinessException - a RuntimeException, so Spring rolled the whole
 * transaction back and took the FAILED row with it. Every failed export
 * therefore vanished without trace, while API_REGISTRY promised the endpoint
 * "logs the attempt either way". REQUIRES_NEW is what makes that promise true:
 * evidence of an attempt must not depend on the attempt succeeding.
 */
@Service
@RequiredArgsConstructor
public class TenantExportLogWriter {

    /** platform_tenant_export.error_detail is VARCHAR(500) - a raw driver message can be far longer. */
    private static final int MAX_ERROR_DETAIL = 500;

    private final PlatformTenantExportRepository repository;
    private final TenantRepository tenantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completed(Long tenantId, Long adminId, TenantExportFormat format,
                          int recordCount, long fileSizeBytes) {
        repository.save(PlatformTenantExport.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .adminId(adminId)
                .format(format)
                .status(TenantExportStatus.COMPLETED)
                .recordCount(recordCount)
                .fileSizeBytes(fileSizeBytes)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long tenantId, Long adminId, TenantExportFormat format, String errorDetail) {
        repository.save(PlatformTenantExport.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .adminId(adminId)
                .format(format)
                .status(TenantExportStatus.FAILED)
                .errorDetail(truncate(errorDetail))
                .build());
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_DETAIL ? value : value.substring(0, MAX_ERROR_DETAIL);
    }
}
