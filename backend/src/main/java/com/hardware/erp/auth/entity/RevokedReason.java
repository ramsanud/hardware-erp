package com.hardware.erp.auth.entity;

/** Why a refresh token stopped being usable. Read during incident review. */
public enum RevokedReason {
    ROTATED,
    LOGOUT,
    LOGOUT_ALL,
    REUSE_DETECTED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    USER_DEACTIVATED,
    ROLE_CHANGED,
    SESSION_REVOKED
}
