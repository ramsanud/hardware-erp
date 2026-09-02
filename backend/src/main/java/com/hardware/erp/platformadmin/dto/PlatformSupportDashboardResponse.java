package com.hardware.erp.platformadmin.dto;

public record PlatformSupportDashboardResponse(
        long open,
        long inProgress,
        long waitingForUser,
        long highPriorityOrUrgent,
        long assignedToMe,
        long resolvedToday
) {}
