package com.hardware.erp.platformadmin.dto;

import java.util.List;

public record PlatformSecurityDashboardResponse(
        long failedLoginsToday,
        long mfaChallengeFailuresToday,
        long accountsLockedToday,
        long totalAdmins,
        long adminsWithMfaEnabled,
        long activeSessions,
        List<PlatformAuditLogResponse> recentPrivilegedActions
) {}
