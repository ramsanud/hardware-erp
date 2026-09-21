package com.hardware.erp.document.entity;

/** CR-101. Mirrors report_job.status's CHECK constraint - see V62. */
public enum ReportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
