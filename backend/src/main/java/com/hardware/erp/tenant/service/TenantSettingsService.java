package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.dto.TenantSettingsRequest;
import com.hardware.erp.tenant.dto.TenantSettingsResponse;

public interface TenantSettingsService {

    TenantSettingsResponse get();

    TenantSettingsResponse update(TenantSettingsRequest request);
}
