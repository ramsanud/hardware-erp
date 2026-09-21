package com.hardware.erp.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobRequest;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobResponse;
import com.hardware.erp.document.entity.ReportJob;
import com.hardware.erp.document.entity.ReportJobFormat;
import com.hardware.erp.document.entity.ReportJobStatus;
import com.hardware.erp.document.repository.ReportJobRepository;
import com.hardware.erp.document.repository.ReportJobRepository.Summary;
import com.hardware.erp.document.service.ReportJobService;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/**
 * CR-101. {@code enqueue} deliberately carries no {@code @Transactional} of
 * its own: {@code repository.save(job)} is itself transactional (Spring
 * Data wraps every repository method), and that commit must be visible
 * before {@link ReportJobWorker#process} - running on a different thread,
 * possibly before this method returns - tries to read the row. Wrapping
 * both in one outer transaction would let the worker start against a row
 * the database has not committed yet.
 */
@Service
@RequiredArgsConstructor
public class ReportJobServiceImpl implements ReportJobService {

    private final ReportJobRepository repository;
    private final ReportJobWorker worker;
    private final ObjectMapper objectMapper;

    @Override
    public ReportJobResponse enqueue(ReportJobRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Long userId = SecurityUtils.currentUserId().orElse(null);

        ReportJob job = ReportJob.builder()
                .tenantId(tenantId)
                .requestedBy(userId)
                .reportType(request.reportType().toUpperCase(Locale.ROOT))
                .format(request.format())
                .paramsJson(toJson(request.params()))
                .status(ReportJobStatus.PENDING)
                .build();
        job = repository.save(job);

        worker.process(job.getId());
        return toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportJobResponse status(Long jobId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Summary summary = repository.findSummary(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Report job", jobId));
        return toResponse(summary);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportJobResponse> list(Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(repository.pageSummaries(tenantId, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadableFile download(Long jobId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ReportJob job = repository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Report job", jobId));
        if (job.getStatus() != ReportJobStatus.COMPLETED) {
            throw new ResourceNotFoundException(
                    "Report job " + jobId + " is " + job.getStatus() + ", not ready for download");
        }
        return new DownloadableFile(job.getFileName(), contentType(job.getFormat()), job.getFileData());
    }

    private ReportJobResponse toResponse(ReportJob job) {
        return new ReportJobResponse(job.getId(), job.getReportType(), job.getFormat(), job.getStatus(),
                job.getFileName(), job.getFileSizeBytes(), job.getErrorMessage(), job.getCreatedAt(), job.getCompletedAt());
    }

    private ReportJobResponse toResponse(Summary s) {
        return new ReportJobResponse(s.getId(), s.getReportType(), s.getFormat(), s.getStatus(),
                s.getFileName(), s.getFileSizeBytes(), s.getErrorMessage(), s.getCreatedAt(), s.getCompletedAt());
    }

    private static String contentType(ReportJobFormat format) {
        return switch (format) {
            case PDF -> "application/pdf";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case CSV -> "text/csv;charset=UTF-8";
            case PNG -> "image/png";
            case JSON -> "application/json";
        };
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            return "{}";
        }
    }
}
