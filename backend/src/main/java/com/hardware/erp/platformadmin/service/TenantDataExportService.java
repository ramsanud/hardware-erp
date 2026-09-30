package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.dto.TenantExportLogResponse;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface TenantDataExportService {

    /**
     * CR-059: takes the HttpServletRequest so the export lifecycle can be
     * written to platform_audit_log with the acting admin's IP and user
     * agent, exactly like every other platform-admin mutation. Null request
     * is tolerated (PlatformAuditService already handles it) for callers
     * with no HTTP context.
     */
    byte[] export(Long tenantId, TenantExportFormat format, Long adminId, HttpServletRequest request);

    List<TenantExportLogResponse> history(Long tenantId);

    /**
     * CR-092. The same snapshot bytes as export(), with none of the
     * platform-admin audit trail around it - the tenant-facing backup
     * (TenantBackupService) keeps its own history row and activity-log entry.
     * A new method rather than a flag on export(), so no existing caller
     * changes (coding-rules: a provider gains a capability via a new method).
     */
    Snapshot buildSnapshot(Long tenantId, TenantExportFormat format);

    record Snapshot(byte[] body, int recordCount) {}
}
