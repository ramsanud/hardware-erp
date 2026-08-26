package com.hardware.erp.product.extraction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Expected header row (case-insensitive, any order): Product Name, Product
 * Code, Category, Brand, Unit, HSN Code, GST %, Purchase Price, Selling
 * Price, MRP, Minimum Stock, Reorder Level. Only Product Name, Unit, GST %,
 * Purchase Price, Selling Price and MRP are required.
 */
@Component
public class ProductCsvExtractionService implements ProductDocumentExtractionService {

    /** Same decompression-bomb / giant-file guard as the Purchase module's CSV reader. */
    private static final int MAX_ROWS = 20_000;

    @Override
    public boolean supports(String extension) {
        return "csv".equalsIgnoreCase(extension);
    }

    @Override
    public ProductExtractionResult extract(InputStream input) throws IOException {
        List<ExtractedProductRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true)
                .setIgnoreHeaderCase(true).setAllowMissingColumnNames(true)
                .build();

        try (CSVParser parser = new CSVParser(new InputStreamReader(input, StandardCharsets.UTF_8), format)) {
            int rowNumber = 1;
            for (CSVRecord record : parser) {
                rowNumber++;
                if (rowNumber - 1 > MAX_ROWS) {
                    warnings.add("File has more than " + MAX_ROWS + " rows - only the first " + MAX_ROWS + " were read.");
                    break;
                }
                if (isBlankRecord(record)) continue;
                rows.add(ProductRowParsing.parse(rowNumber, record.toMap()));
            }
        }
        return new ProductExtractionResult(rows, warnings);
    }

    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) return false;
        }
        return true;
    }
}
