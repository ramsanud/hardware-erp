package com.hardware.erp.document.service;

import com.hardware.erp.document.entity.ReportJobFormat;

import java.util.Map;

/**
 * CR-101. The seam between the async worker and the report/GSTR-1 logic
 * CR-086/CR-087 already built - the worker knows nothing about a report's
 * own columns or query, only how to ask for one by code and get bytes back.
 */
public interface ReportJobRenderer {

    /** Every {@code reportType} this renderer answers to, upper snake case (e.g. "DAY_BOOK", "GSTR1"). */
    boolean supports(String reportType);

    /**
     * Builds the file. Runs inside the tenant's own security context (see
     * ReportJobServiceImpl) so every call into ReportService/Gstr1Service
     * resolves the same tenant the job was queued for - never a parameter.
     *
     * @throws com.hardware.erp.common.exception.BusinessException for an
     *         unknown reportType, a format that report cannot produce, or
     *         params that fail the same validation the synchronous endpoint
     *         applies (e.g. a reversed date range)
     */
    RenderedFile render(String reportType, ReportJobFormat format, Map<String, String> params);

    record RenderedFile(byte[] bytes, String fileName, String contentType) {
    }
}
