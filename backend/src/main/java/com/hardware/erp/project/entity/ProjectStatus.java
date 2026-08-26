package com.hardware.erp.project.entity;

/**
 * Lifecycle only - the business result (won/lost) is the separate,
 * independently-nullable {@link ProjectOutcome}, set only once a project
 * reaches {@link #COMPLETED}. See V18 migration comment for the full
 * reasoning against collapsing these into one enum.
 */
public enum ProjectStatus {
    UPCOMING,
    IN_PROGRESS,
    ON_HOLD,
    CANCELLED,
    COMPLETED
}
