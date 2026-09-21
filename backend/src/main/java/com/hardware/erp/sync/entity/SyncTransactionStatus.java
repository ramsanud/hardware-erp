package com.hardware.erp.sync.entity;

/**
 * CR-091 Phase 9. PENDING is transient (never actually persisted here -
 * the row is written only once the attempt has already happened), kept
 * for the client-side state machine the brief describes; the server only
 * ever writes SYNCED, FAILED or CONFLICT.
 */
public enum SyncTransactionStatus {
    PENDING, SYNCED, FAILED, CONFLICT
}
