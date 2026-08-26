package com.hardware.erp.purchase.extraction;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelDocumentExtractionServiceTest {

    private final ExcelDocumentExtractionService service = new ExcelDocumentExtractionService();

    @Test
    void parsesARealWorkbookRoundTrip() throws Exception {
        byte[] xlsx = buildWorkbook(new String[][]{
                { "Product Name", "Brand", "Category", "SKU", "Quantity", "Unit", "Unit Price", "GST %" },
                { "Claw Hammer 500g", "Godrej", "Hand Tools", "HT-001", "25", "PCS", "180.00", "18" },
                { "Measuring Tape 5m", "Godrej", "Hand Tools", "", "40", "PCS", "80", "18" },
        });

        ExtractionResult result = service.extract(new ByteArrayInputStream(xlsx));

        assertThat(result.rows()).hasSize(2);
        ExtractedRow first = result.rows().get(0);
        assertThat(first.getProductName()).isEqualTo("Claw Hammer 500g");
        assertThat(first.getBrandName()).isEqualTo("Godrej");
        assertThat(first.getQuantity()).isEqualByComparingTo("25");
        assertThat(first.getUnitPriceRupees()).isEqualByComparingTo("180.00");
        assertThat(first.getErrors()).isEmpty();

        // Row numbering must match the CSV extractor's convention (header = row 1, first data row = row 2), so the frontend's "Row N" messaging is identical regardless of which file type was uploaded.
        assertThat(first.getRowNumber()).isEqualTo(2);
    }

    @Test
    void skipsRowsThatAreEntirelyBlank() throws Exception {
        byte[] xlsx = buildWorkbook(new String[][]{
                { "Product Name", "Quantity", "Unit Price" },
                { "Hammer", "2", "150" },
                { "", "", "" },
                { "Wrench", "3", "200" },
        });

        ExtractionResult result = service.extract(new ByteArrayInputStream(xlsx));

        assertThat(result.rows()).extracting(ExtractedRow::getProductName)
                .containsExactly("Hammer", "Wrench");
    }

    @Test
    void reportsAnEmptySheetWithoutThrowing() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1");
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        ExtractionResult result = service.extract(new ByteArrayInputStream(xlsx));

        assertThat(result.rows()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }

    private byte[] buildWorkbook(String[][] data) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
