package com.hardware.erp.platformadmin.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse;
import com.hardware.erp.platformadmin.service.TenantAnalyticsExportService;
import com.hardware.erp.platformadmin.service.TenantAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CR-057 phase 10 - Tenant Analytics. ANALYTICS_VIEW/ANALYTICS_EXPORT enforced here, not just hidden in the frontend. */
@RestController
@RequestMapping("/v1/platform-admin/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform Admin - Analytics", description = "Tenant growth, module adoption and churn, aggregated server-side")
public class PlatformAdminAnalyticsController {

    private final TenantAnalyticsService analyticsService;
    private final TenantAnalyticsExportService exportService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Growth, module usage and churn - last 12 months, aggregated server-side")
    public ResponseEntity<ApiResponse<TenantAnalyticsResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.overview()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ANALYTICS_EXPORT')")
    @Operation(summary = "Export the same analytics data as CSV, XLSX or PDF")
    public ResponseEntity<byte[]> export(@RequestParam("format") String format) {
        byte[] body;
        MediaType contentType;
        String filename;
        switch (format.toLowerCase()) {
            case "csv" -> {
                body = exportService.exportCsv();
                contentType = MediaType.parseMediaType("text/csv");
                filename = "tenant-analytics.csv";
            }
            case "xlsx" -> {
                body = exportService.exportXlsx();
                contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                filename = "tenant-analytics.xlsx";
            }
            case "pdf" -> {
                body = exportService.exportPdf();
                contentType = MediaType.APPLICATION_PDF;
                filename = "tenant-analytics.pdf";
            }
            default -> throw new com.hardware.erp.common.exception.BusinessException(
                    "format must be one of: csv, xlsx, pdf");
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
