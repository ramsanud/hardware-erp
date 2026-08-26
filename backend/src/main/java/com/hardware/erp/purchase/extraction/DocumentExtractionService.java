package com.hardware.erp.purchase.extraction;

import java.io.InputStream;

/**
 * Genuinely pluggable per spec §17 - the Purchase module never depends on
 * one specific parser. Today: CsvDocumentExtractionService and
 * ExcelDocumentExtractionService, both real and deterministic. A future
 * OcrDocumentExtractionService (PDF/image, needs a configured vision-
 * capable AI provider) is a new implementation of this same interface,
 * not a rewrite of anything that calls it.
 */
public interface DocumentExtractionService {

    boolean supports(String extension);

    /** Row 1 is always treated as a header row and skipped. Caps at a bounded row count internally (decompression-bomb guard) - see each implementation. */
    ExtractionResult extract(InputStream input) throws Exception;
}
