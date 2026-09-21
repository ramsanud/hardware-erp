package com.hardware.erp.document.dto;

import com.hardware.erp.document.entity.ReportJobFormat;
import com.hardware.erp.document.entity.ReportJobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;

/** CR-101. Request/response shapes for the async document job endpoints. */
public final class ReportJobDtos {

    private ReportJobDtos() {
    }

    /**
     * {@code reportType} names which {@link com.hardware.erp.document.service.ReportJobRenderer}
     * builds the file - "DAY_BOOK", "RECEIVABLES_AGEING", "STOCK_VALUATION",
     * "PURCHASE_REGISTER", "GST_SUMMARY", "GSTR1" today, the same codes
     * ReportController's synchronous paths already answer to. {@code params}
     * carries that report's own filters (from/to, asOf, period) as plain
     * strings - the same ones the query parameters on the synchronous
     * /export endpoint take, so a renderer reads them identically either way.
     */
    public record ReportJobRequest(
            @NotBlank String reportType,
            @NotNull ReportJobFormat format,
            Map<String, String> params
    ) {
    }

    public record ReportJobResponse(
            Long id,
            String reportType,
            ReportJobFormat format,
            ReportJobStatus status,
            String fileName,
            Integer fileSizeBytes,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
    }
}
