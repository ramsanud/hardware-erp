package com.hardware.erp.purchase.extraction;

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
 * Expected header row (case-insensitive, any order): Product Name, Brand,
 * Category, SKU, Quantity, Unit, Unit Price, GST %. Only Product Name,
 * Quantity and Unit Price are required - the rest may be blank (e.g. a
 * product with no brand, or one that already exists so GST is picked up
 * from the matched product instead - see PurchaseImportServiceImpl).
 */
@Component
public class CsvDocumentExtractionService implements DocumentExtractionService {

    /** Decompression-bomb / accidental-giant-file guard - a real hardware shop bill is a few dozen to a few hundred lines; 20,000 covers genuine bulk imports with real headroom. */
    private static final int MAX_ROWS = 20_000;

    @Override
    public boolean supports(String extension) {
        return "csv".equalsIgnoreCase(extension);
    }

    @Override
    public ExtractionResult extract(InputStream input) throws IOException {
        List<ExtractedRow> rows = new ArrayList<>();
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
                rows.add(RowParsing.parse(rowNumber, record.toMap()));
            }
        }
        return new ExtractionResult(rows, warnings);
    }

    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) return false;
        }
        return true;
    }
}
