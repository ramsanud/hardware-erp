package com.hardware.erp.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.document.entity.ReportJobFormat;
import com.hardware.erp.document.service.ReportJobRenderer;
import com.hardware.erp.report.export.DocumentImageRenderer;
import com.hardware.erp.report.export.ReportDocument;
import com.hardware.erp.report.export.ReportDocuments;
import com.hardware.erp.report.export.ReportExporter;
import com.hardware.erp.report.gst.Gstr1Service;
import com.hardware.erp.report.service.ReportService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CR-101. Answers for the six report codes that already exist - CR-086's
 * five operational reports plus CR-087's GSTR-1 - by calling the same
 * {@link ReportService}/{@link Gstr1Service} the synchronous endpoints use
 * and handing the result to {@link ReportExporter}/{@link DocumentImageRenderer}.
 * No report logic is duplicated or re-derived here.
 */
@Component
@RequiredArgsConstructor
public class StandardReportJobRenderer implements ReportJobRenderer {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final Set<String> KNOWN = Set.of(
            "DAY_BOOK", "RECEIVABLES_AGEING", "STOCK_VALUATION", "PURCHASE_REGISTER", "GST_SUMMARY", "GSTR1");

    private final ReportService reportService;
    private final Gstr1Service gstr1Service;
    private final ReportExporter exporter;
    private final DocumentImageRenderer imageRenderer;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String reportType) {
        return reportType != null && KNOWN.contains(reportType.toUpperCase(Locale.ROOT));
    }

    @Override
    public RenderedFile render(String reportType, ReportJobFormat format, Map<String, String> params) {
        String type = reportType == null ? "" : reportType.toUpperCase(Locale.ROOT);
        Map<String, String> p = params == null ? Map.of() : params;

        if (type.equals("GSTR1")) {
            return renderGstr1(p, format);
        }

        ReportDocument document = switch (type) {
            case "DAY_BOOK" -> ReportDocuments.dayBook(reportService.dayBook(from(p), to(p)), shopName());
            case "RECEIVABLES_AGEING" -> ReportDocuments.receivablesAgeing(
                    reportService.receivablesAgeing(optionalDate(p, "asOf")), shopName());
            case "STOCK_VALUATION" -> ReportDocuments.stockValuation(reportService.stockValuation(), shopName());
            case "PURCHASE_REGISTER" -> ReportDocuments.purchaseRegister(
                    reportService.purchaseRegister(from(p), to(p)), shopName());
            case "GST_SUMMARY" -> ReportDocuments.gstSummary(reportService.gstSummary(from(p), to(p)), shopName());
            default -> throw new BusinessException("Unknown report type: " + reportType);
        };

        String base = type.toLowerCase(Locale.ROOT).replace('_', '-') + "-" + LocalDate.now();
        return switch (format) {
            case PDF -> new RenderedFile(exporter.toPdf(document), base + ".pdf", MediaType.APPLICATION_PDF_VALUE);
            case XLSX -> new RenderedFile(exporter.toXlsx(document), base + ".xlsx", XLSX.toString());
            case CSV -> new RenderedFile(exporter.toCsv(document), base + ".csv", "text/csv;charset=UTF-8");
            case PNG -> new RenderedFile(imageRenderer.toPng(document), base + ".png", MediaType.IMAGE_PNG_VALUE);
            case JSON -> throw new BusinessException(
                    "\"" + reportType + "\" has no JSON export - choose PDF, Excel, CSV or Image.");
        };
    }

    private RenderedFile renderGstr1(Map<String, String> params, ReportJobFormat format) {
        if (format != ReportJobFormat.JSON) {
            throw new BusinessException(
                    "GSTR-1 has no visual layout to export as " + format + " - it is a filing JSON only.");
        }
        String period = params.get("period");
        if (period == null || period.isBlank()) {
            throw new BusinessException("period (MMYYYY) is required for GSTR-1");
        }
        try {
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(gstr1Service.build(period)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new RenderedFile(bytes, "GSTR1-" + period + ".json", MediaType.APPLICATION_JSON_VALUE);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new UncheckedIOException(new java.io.IOException(e));
        }
    }

    private String shopName() {
        return tenantRepository.findById(SecurityUtils.requireCurrentTenantId())
                .map(Tenant::getName).orElse(null);
    }

    private static LocalDate from(Map<String, String> p) {
        return requireDate(p, "from");
    }

    private static LocalDate to(Map<String, String> p) {
        return requireDate(p, "to");
    }

    private static LocalDate requireDate(Map<String, String> p, String key) {
        String value = p.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + " is required (ISO date, e.g. 2026-09-01)");
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(key + " must be an ISO date (e.g. 2026-09-01)");
        }
    }

    private static LocalDate optionalDate(Map<String, String> p, String key) {
        String value = p.get(key);
        return value == null || value.isBlank() ? null : requireDate(p, key);
    }
}
