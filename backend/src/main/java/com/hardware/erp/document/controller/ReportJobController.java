package com.hardware.erp.document.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobRequest;
import com.hardware.erp.document.dto.ReportJobDtos.ReportJobResponse;
import com.hardware.erp.document.service.ReportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * CR-101. The async counterpart to ReportController's synchronous
 * /v1/reports/{report}/export - same reports, plus PNG and GSTR-1's JSON,
 * built off the request thread. Gated on REPORT_VIEW like the reports
 * themselves; a GSTR-1 job additionally requires REPORT_FINANCIAL, checked
 * here rather than in a compound @PreAuthorize SpEL expression against the
 * request body, because this project's build does not carry -parameters
 * and a body-field SpEL reference is not reliable without it.
 */
@RestController
@RequestMapping("/v1/documents/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Document Jobs", description = "CR-101 - async PDF/Excel/CSV/Image/JSON exports")
@PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
public class ReportJobController {

    private final ReportJobService reportJobService;

    @PostMapping
    @Operation(summary = "Queue a background export; returns immediately with a PENDING/PROCESSING job")
    public ResponseEntity<ApiResponse<ReportJobResponse>> enqueue(@Valid @RequestBody ReportJobRequest request) {
        requireGstr1PermissionIfNeeded(request.reportType());
        return ResponseEntity.accepted().body(ApiResponse.ok(reportJobService.enqueue(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Poll one job's status")
    public ApiResponse<ReportJobResponse> status(@PathVariable Long id) {
        return ApiResponse.ok(reportJobService.status(id));
    }

    @GetMapping
    @Operation(summary = "This tenant's jobs, newest first")
    public ApiResponse<PageResponse<ReportJobResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reportJobService.list(PageRequest.of(page, Math.min(size, 200))));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "The finished file - 404 until the job is COMPLETED")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        var file = reportJobService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("Content-Disposition", "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.bytes());
    }

    private void requireGstr1PermissionIfNeeded(String reportType) {
        if (reportType == null || !reportType.toUpperCase(Locale.ROOT).equals("GSTR1")) {
            return;
        }
        boolean hasFinancial = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(
                        com.hardware.erp.auth.entity.PermissionCode.REPORT_FINANCIAL));
        if (!hasFinancial) {
            throw new AccessDeniedException("REPORT_FINANCIAL is required to queue a GSTR-1 export");
        }
    }
}
