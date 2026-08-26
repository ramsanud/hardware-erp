package com.hardware.erp.common.activity;

import java.util.Map;

/**
 * Business record history, used by every module from Module 2 onwards.
 *
 * Not to be confused with SecurityAuditService, which records logins, token
 * misuse and password or role changes. Keeping them apart means a security
 * review is not wading through supplier edits (CR-015).
 */
public interface ActivityLogService {

    void created(String moduleCode, String entityType, Long entityId,
                 String entityLabel, Map<String, Object> newValues);

    /** Records only the fields that actually changed. */
    void updated(String moduleCode, String entityType, Long entityId,
                 String entityLabel, Map<String, Object> before, Map<String, Object> after);

    void deleted(String moduleCode, String entityType, Long entityId,
                 String entityLabel, String remarks);

    void action(String moduleCode, String entityType, Long entityId,
                String entityLabel, ActivityAction action, String remarks);
}
