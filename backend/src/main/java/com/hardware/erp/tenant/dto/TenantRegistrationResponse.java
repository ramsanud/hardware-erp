package com.hardware.erp.tenant.dto;

/** No access token issued here - the owner signs in separately via the normal /v1/auth/login, same as any user (keeps token issuance in exactly one place). */
public record TenantRegistrationResponse(
        Long tenantId,
        String slug,
        String shopName,
        String ownerMobileNo
) {}
