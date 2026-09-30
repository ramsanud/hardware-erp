package com.hardware.erp.document.entity;

/**
 * CR-101. The formats a background job can be asked to build. Distinct from
 * {@code ReportFormat} on the frontend and from the "pdf"/"xlsx" strings
 * ReportController's synchronous /export endpoints already accept - this
 * enum is the async path's own. PNG and JSON are new to it: CR-086/CR-087
 * never needed an image, and GSTR-1's JSON (Gstr1Service) never had a job
 * queue of its own before this.
 */
public enum ReportJobFormat {
    PDF,
    XLSX,
    CSV,
    PNG,
    JSON
}
