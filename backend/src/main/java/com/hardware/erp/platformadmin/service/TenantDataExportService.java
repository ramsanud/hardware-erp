package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.dto.TenantExportLogResponse;
import com.hardware.erp.platformadmin.entity.TenantExportFormat;

import java.util.List;

public interface TenantDataExportService {

    byte[] export(Long tenantId, TenantExportFormat format, Long adminId);

    List<TenantExportLogResponse> history(Long tenantId);
}
