package com.hardware.erp.report.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.report.dto.ReportDtos.*;
import com.hardware.erp.report.export.ReportDocument;
import com.hardware.erp.report.export.ReportDocuments;
import com.hardware.erp.report.export.ReportExporter;
import com.hardware.erp.report.service.ReportService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

/**
 * CR-086. Five operational reports, each as JSON for the screen and as a
 * PDF or XLSX download built from the very same object.
 *
 * Gated on REPORT_VIEW, the permission the sidebar's "Reports" entry has
 * been waiting on since V1 (MANAGER holds it; REPORT_FINANCIAL stays on the
 * accounting-grade exports - Tally, GSTR-1). Tenant comes from the token in
 * ReportServiceImpl, never from a parameter.
 */
@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "CR-086 - Day Book, Receivables Ageing, Stock Valuation, Purchase Register, GST Summary")
@PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_VIEW)")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;
    private final ReportExporter exporter;
    private final TenantRepository tenantRepository;

    // ------------------------------------------------------------ Day Book

    @GetMapping("/day-book")
    @Operation(summary = "Every sale, receipt, credit note, purchase and expense in a date range, in voucher order")
    public ResponseEntity<ApiResponse<DayBookReport>> dayBook(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.dayBook(from, to)));
    }

    @GetMapping("/day-book/export")
    public ResponseEntity<byte[]> dayBookExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String format) {
        return file(ReportDocuments.dayBook(reportService.dayBook(from, to), shopName()),
                "day-book-" + from + "-to-" + to, format);
    }

    // -------------------------------------------------- Receivables Ageing

    @GetMapping("/receivables-ageing")
    @Operation(summary = "Balance due per customer, bucketed 0-30 / 31-60 / 61-90 / 90+ days from the invoice date")
    public ResponseEntity<ApiResponse<ReceivablesAgeingReport>> receivablesAgeing(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.receivablesAgeing(asOf)));
    }

    @GetMapping("/receivables-ageing/export")
    public ResponseEntity<byte[]> receivablesAgeingExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam String format) {
        ReceivablesAgeingReport report = reportService.receivablesAgeing(asOf);
        return file(ReportDocuments.receivablesAgeing(report, shopName()),
                "receivables-ageing-" + report.asOf(), format);
    }

    // ------------------------------------------------------ Stock Valuation

    @GetMapping("/stock-valuation")
    @Operation(summary = "Stock on hand valued at the current purchase price (and at selling price)")
    public ResponseEntity<ApiResponse<StockValuationReport>> stockValuation() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.stockValuation()));
    }

    @GetMapping("/stock-valuation/export")
    public ResponseEntity<byte[]> stockValuationExport(@RequestParam String format) {
        StockValuationReport report = reportService.stockValuation();
        return file(ReportDocuments.stockValuation(report, shopName()),
                "stock-valuation-" + report.asOf(), format);
    }

    // ---------------------------------------------------- Purchase Register

    @GetMapping("/purchase-register")
    @Operation(summary = "Every supplier bill in a date range with its CGST/SGST/IGST, paid and balance")
    public ResponseEntity<ApiResponse<PurchaseRegisterReport>> purchaseRegister(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.purchaseRegister(from, to)));
    }

    @GetMapping("/purchase-register/export")
    public ResponseEntity<byte[]> purchaseRegisterExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String format) {
        return file(ReportDocuments.purchaseRegister(reportService.purchaseRegister(from, to), shopName()),
                "purchase-register-" + from + "-to-" + to, format);
    }

    // ---------------------------------------------------------- GST Summary

    @GetMapping("/gst-summary")
    @Operation(summary = "Output tax, credit notes and input tax by rate slab, and the net for the period")
    public ResponseEntity<ApiResponse<GstSummaryReport>> gstSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.gstSummary(from, to)));
    }

    @GetMapping("/gst-summary/export")
    public ResponseEntity<byte[]> gstSummaryExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String format) {
        return file(ReportDocuments.gstSummary(reportService.gstSummary(from, to), shopName()),
                "gst-summary-" + from + "-to-" + to, format);
    }

    // --------------------------------------------------------------- shared

    private String shopName() {
        return tenantRepository.findById(SecurityUtils.requireCurrentTenantId())
                .map(Tenant::getName).orElse(null);
    }

    private ResponseEntity<byte[]> file(ReportDocument document, String baseName, String format) {
        String f = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return switch (f) {
            case "pdf" -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=\"" + baseName + ".pdf\"")
                    .body(exporter.toPdf(document));
            case "xlsx" -> ResponseEntity.ok()
                    .contentType(XLSX)
                    .header("Content-Disposition", "attachment; filename=\"" + baseName + ".xlsx\"")
                    .body(exporter.toXlsx(document));
            default -> throw new BusinessException("format must be pdf or xlsx");
        };
    }
}
