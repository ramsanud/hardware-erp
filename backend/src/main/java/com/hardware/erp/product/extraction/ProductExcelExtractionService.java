package com.hardware.erp.product.extraction;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the first sheet only, row 1 as the header - mirrors the Purchase module's ExcelDocumentExtractionService exactly. */
@Component
public class ProductExcelExtractionService implements ProductDocumentExtractionService {

    private static final int MAX_ROWS = 20_000;

    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension);
    }

    @Override
    public ProductExtractionResult extract(java.io.InputStream input) throws Exception {
        List<ExtractedProductRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                warnings.add("The workbook has no sheets.");
                return new ProductExtractionResult(rows, warnings);
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                warnings.add("The first sheet has no header row.");
                return new ProductExtractionResult(rows, warnings);
            }

            Map<Integer, String> headerByColumn = new HashMap<>();
            for (Cell cell : headerRow) {
                headerByColumn.put(cell.getColumnIndex(), formatter.formatCellValue(cell));
            }

            int lastRow = sheet.getLastRowNum();
            int dataRowCount = 0;
            for (int r = headerRow.getRowNum() + 1; r <= lastRow; r++) {
                Row excelRow = sheet.getRow(r);
                if (excelRow == null) continue;
                dataRowCount++;
                if (dataRowCount > MAX_ROWS) {
                    warnings.add("Sheet has more than " + MAX_ROWS + " rows - only the first " + MAX_ROWS + " were read.");
                    break;
                }

                Map<String, String> byColumnName = new HashMap<>();
                boolean anyValue = false;
                for (Map.Entry<Integer, String> entry : headerByColumn.entrySet()) {
                    Cell cell = excelRow.getCell(entry.getKey());
                    String value = cell == null ? "" : formatter.formatCellValue(cell);
                    if (!value.isBlank()) anyValue = true;
                    byColumnName.put(entry.getValue(), value);
                }
                if (!anyValue) continue;

                rows.add(ProductRowParsing.parse(r + 1, byColumnName));
            }
        }
        return new ProductExtractionResult(rows, warnings);
    }
}
