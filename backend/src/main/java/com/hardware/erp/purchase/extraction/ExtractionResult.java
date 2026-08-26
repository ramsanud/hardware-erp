package com.hardware.erp.purchase.extraction;

import java.util.List;

public record ExtractionResult(List<ExtractedRow> rows, List<String> warnings) {}
