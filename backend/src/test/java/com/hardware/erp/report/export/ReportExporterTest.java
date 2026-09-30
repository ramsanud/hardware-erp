package com.hardware.erp.report.export;

import com.hardware.erp.report.export.ReportDocument.Column;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CR-086. The exporter is the one place a wrong figure could reach an auditor, so the markup is asserted, not just "%PDF-". */
class ReportExporterTest {

    private final ReportExporter exporter = new ReportExporter();

    private static ReportDocument sample() {
        return ReportDocument.builder("Day Book")
                .caption("Sara Hardware & Sons")
                .caption("Period: 01-09-2026 to 30-09-2026")
                .table(null,
                        List.of(Column.text("Date"), Column.text("Party"), Column.money("Amount")),
                        List.of(List.of("01-09-2026", "R <Ravi> & Co", "1,50,000.00"),
                                List.of("02-09-2026", "Meena", "250.50")),
                        List.of("Total", "", "1,50,250.50"))
                .build();
    }

    @Test
    @DisplayName("the PDF markup carries every cell, escaped, and a bold totals row")
    void htmlCarriesCellsEscaped() {
        String html = exporter.buildHtml(sample());
        assertThat(html).contains("<h1>Day Book</h1>")
                .contains("Sara Hardware &amp; Sons")
                .contains("R &lt;Ravi&gt; &amp; Co")
                .contains("<td class=\"num\">1,50,000.00</td>")
                .contains("<tr class=\"total\">")
                .doesNotContain("<Ravi>");
        assertThat(exporter.toPdf(sample())).startsWith("%PDF-".getBytes());
    }

    @Test
    @DisplayName("an empty table renders a sentence rather than a header with nothing under it")
    void emptyTableSaysSo() {
        ReportDocument empty = ReportDocument.builder("GST Summary")
                .table("Outward supplies", List.of(Column.text("Rate")), List.of(), List.of("Total"))
                .build();
        assertThat(exporter.buildHtml(empty)).contains("Nothing to show for this period.").doesNotContain("<table>");
    }

    @Test
    @DisplayName("money columns land in Excel as numbers so the owner can still sum them")
    void xlsxMoneyIsNumeric() throws Exception {
        byte[] bytes = exporter.toXlsx(sample());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Day Book");
            // title, 2 captions, blank, header, 2 rows, totals
            var header = sheet.getRow(4);
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Amount");
            var first = sheet.getRow(5);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("R <Ravi> & Co");
            assertThat(first.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(first.getCell(2).getNumericCellValue()).isEqualTo(150000.00);
            var totals = sheet.getRow(7);
            assertThat(totals.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(totals.getCell(2).getNumericCellValue()).isEqualTo(150250.50);
        }
    }

    @Test
    @DisplayName("CR-101: the CSV carries the title, captions, header, rows and totals, with a UTF-8 BOM")
    void csvCarriesEverySection() {
        byte[] bytes = exporter.toCsv(sample());
        // Excel needs the BOM to read Tamil/Rupee correctly - the whole reason it is written at all.
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);

        String csv = new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Day Book")
                .contains("Sara Hardware & Sons")
                .contains("Period: 01-09-2026 to 30-09-2026")
                .contains("Date,Party,Amount")
                // Indian digit grouping means the display amount itself carries commas -
                // exactly the field CSV quoting exists for; the party name has none, so
                // it is left bare even though it carries other punctuation.
                .contains("01-09-2026,R <Ravi> & Co,\"1,50,000.00\"")
                .contains("02-09-2026,Meena,250.50")
                .contains("Total,,\"1,50,250.50\"");
    }

    @Test
    @DisplayName("an empty table's CSV still prints its header - no bare blank section")
    void csvEmptyTableStillPrintsHeader() {
        ReportDocument empty = ReportDocument.builder("GST Summary")
                .table("Outward supplies", List.of(Column.text("Rate")), List.of(), List.of("Total"))
                .build();
        byte[] bytes = exporter.toCsv(empty);
        String csv = new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("GST Summary").contains("Outward supplies").contains("Rate").contains("Total");
    }
}
