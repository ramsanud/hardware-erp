package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.dto.TenantRegistrationResponse;

/** Public, unauthenticated (CR-028) - a brand-new shop provisioning itself, not a user joining an existing one (CR-008's "no self-registration" is about the latter and still stands). */
public interface TenantRegistrationService {

    TenantRegistrationResponse register(TenantRegistrationRequest request);

    boolean isSlugAvailable(String slug);
}
