package com.hardware.erp.document.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobRequest;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobResponse;
import org.springframework.data.domain.Pageable;

/**
 * CR-101. Queues a report/document render and answers status/download for
 * it. Every method reads the current tenant from the security context
 * (never a parameter) for the request-thread calls; the background render
 * itself runs under a reconstructed context for the requesting user - see
 * {@code ReportJobWorker}.
 */
public interface ReportJobService {

    /** Saves a PENDING row and hands it to the background worker; returns immediately. */
    ReportJobResponse enqueue(ReportJobRequest request);

    ReportJobResponse status(Long jobId);

    PageResponse<ReportJobResponse> list(Pageable pageable);

    /** Tenant-scoped; throws ResourceNotFoundException if the job is not COMPLETED yet or belongs to another tenant. */
    DownloadableFile download(Long jobId);

    record DownloadableFile(String fileName, String contentType, byte[] bytes) {
    }
}
