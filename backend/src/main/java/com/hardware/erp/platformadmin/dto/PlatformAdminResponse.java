package com.hardware.erp.platformadmin.dto;

import java.util.Set;

public record PlatformAdminResponse(
        Long id,
        String fullName,
        String email,
        String role,
        Set<String> permissions,
        boolean mfaEnabled,
        String status
) {}
