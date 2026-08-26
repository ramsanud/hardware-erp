package com.hardware.erp.auth.service;

import com.hardware.erp.auth.entity.AuditAction;

public interface SecurityAuditService {

    void success(AuditAction action, Long userId, String fullName,
                 String entityType, Long entityId);

    void failure(AuditAction action, Long userId, String fullName, String failureReason);
}
