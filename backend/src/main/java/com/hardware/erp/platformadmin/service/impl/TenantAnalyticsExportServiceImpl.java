package com.hardware.erp.platformadmin.service.impl;

import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.ChurnPoint;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.GrowthPoint;
import com.hardware.erp.platformadmin.dto.TenantAnalyticsResponse.ModuleUsagePoint;
import com.hardware.erp.platformadmin.service.TenantAnalyticsExportService;
import com.hardware.erp.platformadmin.service.TenantAnalyticsService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * CR-057 phase 10 - all three formats built from the exact same
 * TenantAnalyticsService.overview() data the on-screen charts render, so an
 * exported file can never disagree with what an admin just looked at.
 * Reuses the dependencies already on the classpath (Apache POI - Supplier
 * Bill Import; openhtmltopdf - invoice/quotation PDFs; commons-csv - the
 * same import feature) rather than adding a new export library.
 */
@Service
@RequiredArgsConstructor
public class TenantAnalyticsExportServiceImpl implements TenantAnalyticsExportService {

    private final TenantAnalyticsService analyticsService;

    @Override
    public byte[] exportCsv() {
        TenantAnalyticsResponse data = analyticsService.overview();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            printer.printRecord("Tenant Growth");
            printer.printRecord("Month", "New tenants", "New users", "Active users");
            for (GrowthPoint p : data.growth()) {
                printer.printRecord(p.month(), p.newTenants(), p.newUsers(), p.activeUsers());
            }
            printer.println();

            printer.printRecord("Module Usage (of " + data.activeTenantsNow() + " active tenants)");
            printer.printRecord("Module", "Tenants using", "Adoption %");
            for (ModuleUsagePoint p : data.moduleUsage()) {
                printer.printRecord(p.module(), p.tenantsUsing(), String.format("%.1f", p.adoptionPercent()));
            }
            printer.println();

            printer.printRecord("Churn (approximation - see report notes)");
            printer.printRecord("Month", "Tenants suspended", "Total tenants by month end", "Churn rate %");
            for (ChurnPoint p : data.churn()) {
                printer.printRecord(p.month(), p.tenantsSuspended(), p.totalTenantsByMonthEnd(),
                        p.churnRatePercent() == null ? "" : String.format("%.2f", p.churnRatePercent()));
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to build analytics CSV export", new java.io.IOException(e));
        }
        return out.toByteArray();
    }

    @Override
    public byte[] exportXlsx() {
        TenantAnalyticsResponse data = analyticsService.overview();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet growthSheet = workbook.createSheet("Tenant Growth");
            writeHeader(growthSheet, "Month", "New tenants", "New users", "Active users");
            int r = 1;
            for (GrowthPoint p : data.growth()) {
                Row row = growthSheet.createRow(r++);
                row.createCell(0).setCellValue(p.month());
                row.createCell(1).setCellValue(p.newTenants());
                row.createCell(2).setCellValue(p.newUsers());
                row.createCell(3).setCellValue(p.activeUsers());
            }

            Sheet usageSheet = workbook.createSheet("Module Usage");
            writeHeader(usageSheet, "Module", "Tenants using", "Adoption %");
            r = 1;
            for (ModuleUsagePoint p : data.moduleUsage()) {
                Row row = usageSheet.createRow(r++);
                row.createCell(0).setCellValue(p.module());
                row.createCell(1).setCellValue(p.tenantsUsing());
                row.createCell(2).setCellValue(Math.round(p.adoptionPercent() * 10) / 10.0);
            }

            Sheet churnSheet = workbook.createSheet("Churn");
            writeHeader(churnSheet, "Month", "Tenants suspended", "Total tenants by month end", "Churn rate %");
            r = 1;
            for (ChurnPoint p : data.churn()) {
                Row row = churnSheet.createRow(r++);
                row.createCell(0).setCellValue(p.month());
                row.createCell(1).setCellValue(p.tenantsSuspended());
                row.createCell(2).setCellValue(p.totalTenantsByMonthEnd());
                Cell rate = row.createCell(3);
                if (p.churnRatePercent() != null) rate.setCellValue(p.churnRatePercent());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to build analytics XLSX export", new java.io.IOException(e));
        }
    }

    private void writeHeader(Sheet sheet, String... columns) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }
    }

    @Override
    public byte[] exportPdf() {
        TenantAnalyticsResponse data = analyticsService.overview();
        String html = buildHtml(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to render analytics PDF", new java.io.IOException(e));
        }
        return out.toByteArray();
    }

    private String buildHtml(TenantAnalyticsResponse data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>")
          .append("body{font-family:sans-serif;font-size:11px;} h2{margin-top:24px;} ")
          .append("table{width:100%;border-collapse:collapse;margin-top:6px;} ")
          .append("th,td{border:1px solid #ccc;padding:4px 8px;text-align:left;}")
          .append("</style></head><body>");
        sb.append("<h1>Tenant Analytics</h1>");
        sb.append("<p>Active tenants now: ").append(data.activeTenantsNow()).append("</p>");

        sb.append("<h2>Tenant Growth (last 12 months)</h2><table><tr><th>Month</th><th>New tenants</th><th>New users</th><th>Active users</th></tr>");
        for (GrowthPoint p : data.growth()) {
            sb.append("<tr><td>").append(p.month()).append("</td><td>").append(p.newTenants())
              .append("</td><td>").append(p.newUsers()).append("</td><td>").append(p.activeUsers()).append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<h2>Module Usage</h2><table><tr><th>Module</th><th>Tenants using</th><th>Adoption %</th></tr>");
        for (ModuleUsagePoint p : data.moduleUsage()) {
            sb.append("<tr><td>").append(p.module()).append("</td><td>").append(p.tenantsUsing())
              .append("</td><td>").append(String.format("%.1f", p.adoptionPercent())).append("%</td></tr>");
        }
        sb.append("</table>");

        sb.append("<h2>Churn (approximation)</h2>")
          // &#247; not &divide;: openhtmltopdf parses strict XHTML, where the only
          // predefined entities are &amp;/&lt;/&gt;/&quot;/&apos; - a named HTML
          // entity is an undeclared-entity parse error and fails the whole render
          // with a 500. Caught by PlatformAdminAnalyticsControllerIT, not review.
          .append("<p>Tenants suspended this month &#247; total tenants that exist by month end - ")
          .append("not a cohort or usage-based churn measure.</p>")
          .append("<table><tr><th>Month</th><th>Suspended</th><th>Total tenants</th><th>Churn %</th></tr>");
        for (ChurnPoint p : data.churn()) {
            sb.append("<tr><td>").append(p.month()).append("</td><td>").append(p.tenantsSuspended())
              .append("</td><td>").append(p.totalTenantsByMonthEnd()).append("</td><td>")
              .append(p.churnRatePercent() == null ? "-" : String.format("%.2f%%", p.churnRatePercent()))
              .append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }
}
