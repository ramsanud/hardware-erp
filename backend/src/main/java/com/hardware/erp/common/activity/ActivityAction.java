package com.hardware.erp.common.activity;

/** Must stay in step with ck_activity_action in V3__activity_log.sql. */
public enum ActivityAction {
    CREATE, UPDATE, DELETE, RESTORE, STATUS_CHANGE,
    IMPORT, EXPORT, PRINT, APPROVE, REJECT, CANCEL
}
