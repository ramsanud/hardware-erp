package com.hardware.erp.common.activity;

import org.springframework.stereotype.Component;

/** CR-072. */
@Component
public class ActivityLogMapper {

    public ActivityLogResponse toResponse(ActivityLog entry) {
        return new ActivityLogResponse(
                entry.getId(),
                entry.getModuleCode(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getEntityLabel(),
                entry.getAction(),
                entry.getOldValues(),
                entry.getNewValues(),
                entry.getUserId(),
                entry.getFullName(),
                entry.getRoleCode(),
                entry.getIpAddress(),
                entry.getRequestId(),
                entry.getRemarks(),
                entry.getCreatedAt());
    }
}
