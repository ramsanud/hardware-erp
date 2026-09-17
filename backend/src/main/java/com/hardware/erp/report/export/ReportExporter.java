package com.hardware.erp.report.export;

import com.hardware.erp.report.export.ReportDocument.Column;
import com.hardware.erp.report.export.ReportDocument.Table;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * CR-086. Renders a {@link ReportDocument} as PDF (openhtmltopdf, the same
 * engine as invoice/quotation PDFs) or XLSX (Apache POI, already on the
 * classpath for supplier-bill import). No new dependency.
 *
 * PDF markup is strict XHTML: every value is escaped and only the five
 * predefined entities are used - a named entity is an undeclared-entity
 * parse error that fails the whole render (the lesson recorded on
 * TenantAnalyticsExportServiceImpl).
 */
@Component
public class ReportExporter {

    public byte[] toPdf(ReportDocument document) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(buildHtml(document), null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to render the report PDF", new IOException(e));
        }
        return out.toByteArray();
    }

    /** Package-private so the unit test can assert on the markup, not just on "%PDF-". */
    String buildHtml(ReportDocument document) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<html><head><meta charset=\"UTF-8\"/><style>")
          .append("@page{size:A4 landscape;margin:14mm;} ")
          .append("body{font-family:sans-serif;font-size:10px;color:#111;} ")
          .append("h1{font-size:16px;margin:0 0 4px 0;} h2{font-size:12px;margin:16px 0 4px 0;} ")
          .append("p.caption{margin:0 0 2px 0;color:#555;} ")
          .append("table{width:100%;border-collapse:collapse;margin-top:4px;} ")
          .append("th,td{border:1px solid #bbb;padding:3px 6px;text-align:left;vertical-align:top;} ")
          .append("th{background:#eee;} td.num,th.num{text-align:right;white-space:nowrap;} ")
          .append("tr.total td{font-weight:bold;background:#f6f6f6;} ")
          .append("p.empty{color:#777;font-style:italic;margin:4px 0;}")
          .append("</style></head><body>");
        sb.append("<h1>").append(esc(document.title())).append("</h1>");
        for (String caption : document.captions()) {
            sb.append("<p class=\"caption\">").append(esc(caption)).append("</p>");
        }
        for (Table table : document.tables()) {
            if (table.heading() != null) {
                sb.append("<h2>").append(esc(table.heading())).append("</h2>");
            }
            if (table.rows().isEmpty()) {
                sb.append("<p class=\"empty\">Nothing to show for this period.</p>");
                continue;
            }
            sb.append("<table><thead><tr>");
            for (Column column : table.columns()) {
                sb.append(column.numeric() ? "<th class=\"num\">" : "<th>").append(esc(column.header())).append("</th>");
            }
            sb.append("</tr></thead><tbody>");
            for (List<String> row : table.rows()) {
                appendRow(sb, table.columns(), row, false);
            }
            if (table.totals() != null) {
                appendRow(sb, table.columns(), table.totals(), true);
            }
            sb.append("</tbody></table>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<Column> columns, List<String> row, boolean total) {
        sb.append(total ? "<tr class=\"total\">" : "<tr>");
        for (int i = 0; i < columns.size(); i++) {
            String value = i < row.size() && row.get(i) != null ? row.get(i) : "";
            sb.append(columns.get(i).numeric() ? "<td class=\"num\">" : "<td>").append(esc(value)).append("</td>");
        }
        sb.append("</tr>");
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public byte[] toXlsx(ReportDocument document) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            header.setBorderBottom(BorderStyle.THIN);
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            money.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle moneyTotal = workbook.createCellStyle();
            moneyTotal.cloneStyleFrom(money);
            moneyTotal.setFont(bold);
            CellStyle totalText = workbook.createCellStyle();
            totalText.setFont(bold);

            int sheetNo = 0;
            for (Table table : document.tables()) {
                String name = safeSheetName(table.heading() != null ? table.heading() : document.title(), sheetNo++);
                Sheet sheet = workbook.createSheet(name);
                int r = 0;
                Row titleRow = sheet.createRow(r++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(document.title() + (table.heading() != null ? " - " + table.heading() : ""));
                titleCell.setCellStyle(totalText);
                for (String caption : document.captions()) {
                    sheet.createRow(r++).createCell(0).setCellValue(caption);
                }
                r++;
                Row head = sheet.createRow(r++);
                for (int c = 0; c < table.columns().size(); c++) {
                    Cell cell = head.createCell(c);
                    cell.setCellValue(table.columns().get(c).header());
                    cell.setCellStyle(header);
                }
                for (List<String> row : table.rows()) {
                    writeRow(sheet.createRow(r++), table.columns(), row, money, null);
                }
                if (table.totals() != null) {
                    writeRow(sheet.createRow(r++), table.columns(), table.totals(), moneyTotal, totalText);
                }
                for (int c = 0; c < table.columns().size(); c++) {
                    sheet.setColumnWidth(c, Math.min(60, Math.max(12, widest(table, c) + 2)) * 256);
                }
            }
            if (document.tables().isEmpty()) {
                workbook.createSheet("Report").createRow(0).createCell(0).setCellValue(document.title());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build the report workbook", e);
        }
    }

    private static void writeRow(Row row, List<Column> columns, List<String> values, CellStyle money, CellStyle text) {
        for (int c = 0; c < columns.size(); c++) {
            String value = c < values.size() && values.get(c) != null ? values.get(c) : "";
            Cell cell = row.createCell(c);
            if (columns.get(c).numeric() && !value.isBlank()) {
                // The display string is "1,50,000.00"; Excel wants the number.
                try {
                    cell.setCellValue(new BigDecimal(value.replace(",", "")).doubleValue());
                    cell.setCellStyle(money);
                    continue;
                } catch (NumberFormatException ignored) {
                    // a non-numeric value in a money column (e.g. "-") is written as text below
                }
            }
            cell.setCellValue(value);
            if (text != null) cell.setCellStyle(text);
        }
    }

    private static int widest(Table table, int column) {
        int width = table.columns().get(column).header().length();
        for (List<String> row : table.rows()) {
            if (column < row.size() && row.get(column) != null) width = Math.max(width, row.get(column).length());
        }
        return width;
    }

    /** Excel sheet names: at most 31 characters, none of []:*?/\ and unique within the workbook. */
    private static String safeSheetName(String heading, int index) {
        String cleaned = heading.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (cleaned.length() > 28) cleaned = cleaned.substring(0, 28).trim();
        return index == 0 ? cleaned : cleaned + " " + (index + 1);
    }
}
